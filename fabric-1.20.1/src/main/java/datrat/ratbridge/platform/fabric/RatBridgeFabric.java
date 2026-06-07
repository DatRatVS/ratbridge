package datrat.ratbridge.platform.fabric;

import com.mojang.brigadier.CommandDispatcher;
import datrat.ratbridge.bridge.BridgeConfig;
import datrat.ratbridge.bridge.BridgeConfigFile;
import datrat.ratbridge.bridge.BridgeController;
import datrat.ratbridge.bridge.ValidationResult;
import datrat.ratbridge.discord.DiscordClientFactory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RatBridgeFabric implements ModInitializer {
    public static final String MOD_ID = "ratbridge";
    public static final Logger LOGGER = LoggerFactory.getLogger("RatBridge");

    private final BridgeController bridge = new BridgeController();
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean serverAvailable = new AtomicBoolean(false);
    private final ExecutorService reloadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RatBridge-Reload");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                bridge.onMinecraftChat(sender.getGameProfile().getName(), message.signedContent()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                bridge.onPlayerJoined(handler.player.getGameProfile().getName()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                bridge.onPlayerLeft(handler.player.getGameProfile().getName()));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> registerCommands(dispatcher));
    }

    private void onServerStarted(MinecraftServer server) {
        serverAvailable.set(true);
        BridgeConfig config;
        try {
            config = loadConfig();
        } catch (Exception error) {
            LOGGER.error("RatBridge config could not be loaded; bridge will stay disabled", error);
            return;
        }

        startBridge(server, config, false);
    }

    private void onServerStopping(MinecraftServer server) {
        serverAvailable.set(false);
        bridge.onServerStopping();
        bridge.stop();
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(context -> reloadBridge(context.getSource()))));
    }

    private int reloadBridge(CommandSourceStack source) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("RatBridge reload is already running."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        bridge.stop();

        BridgeConfig config;
        try {
            config = loadConfig();
        } catch (Exception error) {
            reloadInProgress.set(false);
            LOGGER.error("RatBridge config reload failed", error);
            source.sendFailure(Component.literal("RatBridge config reload failed: " + error.getMessage()));
            return 0;
        }

        if (!config.enabled()) {
            reloadInProgress.set(false);
            source.sendSuccess(() -> Component.literal("RatBridge reloaded; bridge is disabled by config."), false);
            return 1;
        }

        ValidationResult validation = config.validate(System::getenv);
        if (!validation.valid()) {
            reloadInProgress.set(false);
            String errors = String.join("; ", validation.errors());
            LOGGER.error("RatBridge config is invalid after reload; bridge will stay disabled: {}", errors);
            source.sendFailure(Component.literal("RatBridge config is invalid: " + errors));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("RatBridge reload started; reconnecting Discord..."), false);
        reloadExecutor.submit(() -> {
            try {
                if (!serverAvailable.get()) {
                    return;
                }
                bridge.start(config, new FabricMinecraftMessageSink(server), () -> DiscordClientFactory.create(config));
                if (!serverAvailable.get()) {
                    bridge.stop();
                    return;
                }
                LOGGER.info("RatBridge reloaded with client={} mode={}", config.client(), config.mode());
                server.execute(() -> source.sendSuccess(() -> Component.literal("RatBridge reloaded and Discord is connected."), false));
            } catch (Exception error) {
                bridge.stop();
                LOGGER.error("RatBridge failed to start after reload; bridge will stay disabled", error);
                server.execute(() -> source.sendFailure(Component.literal("RatBridge reload failed: " + error.getMessage())));
            } finally {
                reloadInProgress.set(false);
            }
        });
        return 1;
    }

    private void startBridge(MinecraftServer server, BridgeConfig config, boolean reloaded) {
        if (!config.enabled()) {
            LOGGER.info("RatBridge is disabled by config");
            return;
        }

        ValidationResult validation = config.validate(System::getenv);
        if (!validation.valid()) {
            LOGGER.error("RatBridge config is invalid; bridge will stay disabled: {}", String.join("; ", validation.errors()));
            return;
        }

        try {
            bridge.start(config, new FabricMinecraftMessageSink(server), () -> DiscordClientFactory.create(config));
            if (!reloaded) {
                bridge.onServerStarted();
            }
            LOGGER.info("RatBridge started with client={} mode={}", config.client(), config.mode());
        } catch (Exception error) {
            bridge.stop();
            LOGGER.error("RatBridge failed to start; bridge will stay disabled", error);
        }
    }

    private BridgeConfig loadConfig() throws Exception {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ratbridge.toml");
        return BridgeConfigFile.load(configPath);
    }
}
