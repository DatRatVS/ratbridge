package datrat.ratbridge.platform.fabric;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import datrat.ratbridge.bridge.AuthenticationDecision;
import datrat.ratbridge.bridge.AuthenticationLoginContext;
import datrat.ratbridge.bridge.AuthenticationLogout;
import datrat.ratbridge.bridge.AuthenticationStore;
import datrat.ratbridge.bridge.AuthenticationStorePaths;
import datrat.ratbridge.bridge.BridgeConfig;
import datrat.ratbridge.bridge.BridgeConfigFile;
import datrat.ratbridge.bridge.BridgeController;
import datrat.ratbridge.bridge.TpsMonitor;
import datrat.ratbridge.bridge.ValidationResult;
import datrat.ratbridge.discord.DiscordClientFactory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RatBridgeFabric implements ModInitializer {
    public static final String MOD_ID = "ratbridge";
    public static final Logger LOGGER = LoggerFactory.getLogger("RatBridge");

    private static RatBridgeFabric instance;

    private final BridgeController bridge = new BridgeController();
    private final TpsMonitor tpsMonitor = new TpsMonitor();
    private final Set<UUID> authenticationRejectedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean serverAvailable = new AtomicBoolean(false);
    private final ExecutorService reloadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RatBridge-Reload");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInitialize() {
        instance = this;
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(server -> tpsMonitor.recordTick());
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                bridge.onMinecraftChat(sender.getGameProfile().getName(), message.signedContent()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                onPlayerJoined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                onPlayerDisconnected(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                String deathMessage = damageSource.getLocalizedDeathMessage(player).getString();
                bridge.onPlayerDied(player.getGameProfile().getName(), deathMessage);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> registerCommands(dispatcher));
    }

    public static void onAdvancementEarned(ServerPlayer player, Advancement advancement) {
        RatBridgeFabric current = instance;
        if (current == null) {
            return;
        }
        DisplayInfo display = advancement.getDisplay();
        if (display == null || !display.shouldAnnounceChat()) {
            return;
        }
        current.bridge.onPlayerAdvancement(
                player.getGameProfile().getName(),
                display.getTitle().getString(),
                display.getDescription().getString()
        );
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
                bridge.start(
                        config,
                        new FabricMinecraftMessageSink(server),
                        new FabricServerStatusProvider(server, tpsMonitor),
                        () -> DiscordClientFactory.create(config),
                        authenticationStore(server),
                        logout -> disconnectAuthenticatedPlayer(server, logout)
                );
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
            bridge.start(
                        config,
                        new FabricMinecraftMessageSink(server),
                        new FabricServerStatusProvider(server, tpsMonitor),
                        () -> DiscordClientFactory.create(config),
                        authenticationStore(server),
                        logout -> disconnectAuthenticatedPlayer(server, logout)
            );
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
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("ratbridge");
        return BridgeConfigFile.loadSplit(configDirectory);
    }

    private void onPlayerJoined(ServerPlayer player) {
        AuthenticationDecision decision = bridge.authenticateLogin(authenticationLoginContext(player));
        if (!decision.allowed()) {
            authenticationRejectedPlayers.add(player.getGameProfile().getId());
            bridge.onUnauthenticatedLogin(
                    player.getGameProfile().getId().toString(),
                    player.getGameProfile().getName()
            );
            player.connection.disconnect(LegacyTextComponents.parse(decision.disconnectMessage()));
            return;
        }
        bridge.onPlayerJoined(player.getGameProfile().getName());
    }

    private void onPlayerDisconnected(ServerPlayer player) {
        if (authenticationRejectedPlayers.remove(player.getGameProfile().getId())) {
            return;
        }
        bridge.onPlayerLeft(player.getGameProfile().getName());
    }

    private AuthenticationLoginContext authenticationLoginContext(ServerPlayer player) {
        GameProfile profile = player.getGameProfile();
        return new AuthenticationLoginContext(
                profile.getId().toString(),
                profile.getName(),
                player.server.getPlayerList().isWhiteListed(profile),
                player.server.getPlayerList().getBans().isBanned(profile)
        );
    }

    private AuthenticationStore authenticationStore(MinecraftServer server) {
        Path targetPath = AuthenticationStorePaths.worldDataPath(server.getWorldPath(LevelResource.ROOT));
        Path legacyPath = FabricLoader.getInstance().getConfigDir().resolve("ratbridge").resolve(AuthenticationStorePaths.FILE_NAME);
        try {
            if (AuthenticationStorePaths.migrateLegacyConfigPath(legacyPath, targetPath)) {
                LOGGER.info("Migrated RatBridge authentication users from {} to {}", legacyPath, targetPath);
            }
        } catch (Exception error) {
            LOGGER.warn("Could not migrate RatBridge authentication users from {} to {}; using the world data path", legacyPath, targetPath, error);
        }
        return new AuthenticationStore(targetPath);
    }

    private void disconnectAuthenticatedPlayer(MinecraftServer server, AuthenticationLogout logout) {
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(logout.minecraftUuid()));
                if (player != null) {
                    player.connection.disconnect(LegacyTextComponents.parse(logout.disconnectMessage()));
                }
            } catch (IllegalArgumentException error) {
                LOGGER.warn("Cannot disconnect logged out player with invalid UUID {}", logout.minecraftUuid());
            }
        });
    }
}
