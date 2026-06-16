package datrat.ratbridge.platform.forge;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import datrat.ratbridge.RatBridge;
import datrat.ratbridge.bridge.BridgeConfig;
import datrat.ratbridge.bridge.BridgeConfigFile;
import datrat.ratbridge.bridge.BridgeController;
import datrat.ratbridge.bridge.AuthenticationDecision;
import datrat.ratbridge.bridge.AuthenticationLoginContext;
import datrat.ratbridge.bridge.AuthenticationLogout;
import datrat.ratbridge.bridge.AuthenticationStore;
import datrat.ratbridge.bridge.AuthenticationStorePaths;
import datrat.ratbridge.bridge.TpsMonitor;
import datrat.ratbridge.bridge.ValidationResult;
import datrat.ratbridge.discord.DiscordClientFactory;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ForgeServerEvents {
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

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        serverAvailable.set(true);
        BridgeConfig config;
        try {
            config = loadConfig();
        } catch (Exception error) {
            RatBridge.LOGGER.error("RatBridge config could not be loaded; bridge will stay disabled", error);
            return;
        }

        if (!config.enabled()) {
            RatBridge.LOGGER.info("RatBridge is disabled by config");
            return;
        }

        ValidationResult validation = config.validate(System::getenv);
        if (!validation.valid()) {
            RatBridge.LOGGER.error("RatBridge config is invalid; bridge will stay disabled: {}", String.join("; ", validation.errors()));
            return;
        }

        try {
            bridge.start(
                    config,
                    new MinecraftServerMessageSink(event.getServer()),
                    new ForgeServerStatusProvider(event.getServer(), tpsMonitor),
                    () -> DiscordClientFactory.create(config),
                    authenticationStore(event.getServer()),
                    logout -> disconnectAuthenticatedPlayer(event.getServer(), logout)
            );
            bridge.onServerStarted();
            RatBridge.LOGGER.info("RatBridge started with client={} mode={}", config.client(), config.mode());
        } catch (Exception error) {
            bridge.stop();
            RatBridge.LOGGER.error("RatBridge failed to start; bridge will stay disabled", error);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("ratbridge")
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tpsMonitor.recordTick();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (authenticationRejectedPlayers.remove(player.getGameProfile().getId())) {
                return;
            }
            bridge.onPlayerLeft(player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String deathMessage = event.getSource().getLocalizedDeathMessage(player).getString();
            bridge.onPlayerDied(player.getGameProfile().getName(), deathMessage);
        }
    }

    @SubscribeEvent
    public void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Advancement advancement = event.getAdvancement();
        DisplayInfo display = advancement.getDisplay();
        if (display == null || !display.shouldAnnounceChat()) {
            return;
        }
        bridge.onPlayerAdvancement(
                player.getGameProfile().getName(),
                display.getTitle().getString(),
                display.getDescription().getString()
        );
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
            RatBridge.LOGGER.error("RatBridge config reload failed", error);
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
            RatBridge.LOGGER.error("RatBridge config is invalid after reload; bridge will stay disabled: {}", errors);
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
                        new MinecraftServerMessageSink(server),
                        new ForgeServerStatusProvider(server, tpsMonitor),
                        () -> DiscordClientFactory.create(config),
                        authenticationStore(server),
                        logout -> disconnectAuthenticatedPlayer(server, logout)
                );
                if (!serverAvailable.get()) {
                    bridge.stop();
                    return;
                }
                RatBridge.LOGGER.info("RatBridge reloaded with client={} mode={}", config.client(), config.mode());
                server.execute(() -> source.sendSuccess(() -> Component.literal("RatBridge reloaded and Discord is connected."), false));
            } catch (Exception error) {
                bridge.stop();
                RatBridge.LOGGER.error("RatBridge failed to start after reload; bridge will stay disabled", error);
                server.execute(() -> source.sendFailure(Component.literal("RatBridge reload failed: " + error.getMessage())));
            } finally {
                reloadInProgress.set(false);
            }
        });
        return 1;
    }

    private BridgeConfig loadConfig() throws Exception {
        Path configDirectory = FMLPaths.CONFIGDIR.get().resolve("ratbridge");
        return BridgeConfigFile.loadSplit(configDirectory);
    }

    private AuthenticationStore authenticationStore(MinecraftServer server) {
        Path targetPath = AuthenticationStorePaths.worldDataPath(server.getWorldPath(LevelResource.ROOT));
        Path legacyPath = FMLPaths.CONFIGDIR.get().resolve("ratbridge").resolve(AuthenticationStorePaths.FILE_NAME);
        try {
            if (AuthenticationStorePaths.migrateLegacyConfigPath(legacyPath, targetPath)) {
                RatBridge.LOGGER.info("Migrated RatBridge authentication users from {} to {}", legacyPath, targetPath);
            }
        } catch (Exception error) {
            RatBridge.LOGGER.warn("Could not migrate RatBridge authentication users from {} to {}; using the world data path", legacyPath, targetPath, error);
        }
        return new AuthenticationStore(targetPath);
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

    private void disconnectAuthenticatedPlayer(MinecraftServer server, AuthenticationLogout logout) {
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(logout.minecraftUuid()));
                if (player != null) {
                    player.connection.disconnect(LegacyTextComponents.parse(logout.disconnectMessage()));
                }
            } catch (IllegalArgumentException error) {
                RatBridge.LOGGER.warn("Cannot disconnect logged out player with invalid UUID {}", logout.minecraftUuid());
            }
        });
    }
}
