package datrat.simplebridge.platform.forge;

import com.mojang.brigadier.CommandDispatcher;
import datrat.simplebridge.SimpleBridge;
import datrat.simplebridge.bridge.BridgeConfig;
import datrat.simplebridge.bridge.BridgeController;
import datrat.simplebridge.bridge.ValidationResult;
import datrat.simplebridge.discord.DiscordClientFactory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ForgeServerEvents {
    private final BridgeController bridge = new BridgeController();
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean serverAvailable = new AtomicBoolean(false);
    private final ExecutorService reloadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SimpleBridge-Reload");
        thread.setDaemon(true);
        return thread;
    });

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        serverAvailable.set(true);
        BridgeConfig config = SimpleBridgeForgeConfig.snapshot();
        if (!config.enabled()) {
            SimpleBridge.LOGGER.info("Simple Bridge is disabled by config");
            return;
        }

        ValidationResult validation = config.validate(System::getenv);
        if (!validation.valid()) {
            SimpleBridge.LOGGER.error("Simple Bridge config is invalid; bridge will stay disabled: {}", String.join("; ", validation.errors()));
            return;
        }

        try {
            bridge.start(config, new MinecraftServerMessageSink(event.getServer()), () -> DiscordClientFactory.create(config));
            bridge.onServerStarted();
            SimpleBridge.LOGGER.info("Simple Bridge started with client={} mode={}", config.client(), config.mode());
        } catch (Exception error) {
            bridge.stop();
            SimpleBridge.LOGGER.error("Simple Bridge failed to start; bridge will stay disabled", error);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("simplebridge")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(context -> reloadBridge(context.getSource()))));
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        serverAvailable.set(false);
        bridge.onServerStopping();
        bridge.stop();
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        bridge.onMinecraftChat(event.getUsername(), event.getRawText());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            bridge.onPlayerJoined(player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            bridge.onPlayerLeft(player.getGameProfile().getName());
        }
    }

    private int reloadBridge(CommandSourceStack source) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("Simple Bridge reload is already running."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        bridge.stop();

        try {
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
        } catch (Exception error) {
            reloadInProgress.set(false);
            SimpleBridge.LOGGER.error("Simple Bridge config reload failed", error);
            source.sendFailure(Component.literal("Simple Bridge config reload failed: " + error.getMessage()));
            return 0;
        }

        BridgeConfig config = SimpleBridgeForgeConfig.snapshot();
        if (!config.enabled()) {
            reloadInProgress.set(false);
            source.sendSuccess(() -> Component.literal("Simple Bridge reloaded; bridge is disabled by config."), false);
            return 1;
        }

        ValidationResult validation = config.validate(System::getenv);
        if (!validation.valid()) {
            reloadInProgress.set(false);
            String errors = String.join("; ", validation.errors());
            SimpleBridge.LOGGER.error("Simple Bridge config is invalid after reload; bridge will stay disabled: {}", errors);
            source.sendFailure(Component.literal("Simple Bridge config is invalid: " + errors));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Simple Bridge reload started; reconnecting Discord..."), false);
        reloadExecutor.submit(() -> {
            try {
                if (!serverAvailable.get()) {
                    return;
                }
                bridge.start(config, new MinecraftServerMessageSink(server), () -> DiscordClientFactory.create(config));
                if (!serverAvailable.get()) {
                    bridge.stop();
                    return;
                }
                SimpleBridge.LOGGER.info("Simple Bridge reloaded with client={} mode={}", config.client(), config.mode());
                server.execute(() -> source.sendSuccess(() -> Component.literal("Simple Bridge reloaded and Discord is connected."), false));
            } catch (Exception error) {
                bridge.stop();
                SimpleBridge.LOGGER.error("Simple Bridge failed to start after reload; bridge will stay disabled", error);
                server.execute(() -> source.sendFailure(Component.literal("Simple Bridge reload failed: " + error.getMessage())));
            } finally {
                reloadInProgress.set(false);
            }
        });
        return 1;
    }
}
