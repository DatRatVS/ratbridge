package datrat.ratbridge.platform.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import datrat.ratbridge.bridge.BridgeConfig;
import datrat.ratbridge.bridge.BridgeConfigFile;
import datrat.ratbridge.bridge.BridgeController;
import datrat.ratbridge.bridge.TpsMonitor;
import datrat.ratbridge.bridge.ValidationResult;
import datrat.ratbridge.discord.DiscordClientFactory;
import datrat.ratbridge.neoforge.RatBridgeNeoForge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NeoForgeServerEvents {
    private final BridgeController bridge = new BridgeController();
    private final TpsMonitor tpsMonitor = new TpsMonitor();
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
            RatBridgeNeoForge.LOGGER.error("RatBridge config could not be loaded; bridge will stay disabled", error);
            return;
        }

        if (!config.enabled()) {
            RatBridgeNeoForge.LOGGER.info("RatBridge is disabled by config");
            return;
        }

        ValidationResult validation = config.validate(System::getenv);
        if (!validation.valid()) {
            RatBridgeNeoForge.LOGGER.error("RatBridge config is invalid; bridge will stay disabled: {}", String.join("; ", validation.errors()));
            return;
        }

        try {
            bridge.start(
                    config,
                    new NeoForgeMinecraftMessageSink(event.getServer()),
                    new NeoForgeServerStatusProvider(event.getServer(), tpsMonitor),
                    () -> DiscordClientFactory.create(config)
            );
            bridge.onServerStarted();
            RatBridgeNeoForge.LOGGER.info("RatBridge started with client={} mode={}", config.client(), config.mode());
        } catch (Exception error) {
            bridge.stop();
            RatBridgeNeoForge.LOGGER.error("RatBridge failed to start; bridge will stay disabled", error);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(RatBridgeNeoForge.MOD_ID)
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
            bridge.onPlayerJoined(player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
        AdvancementDisplay display = readAdvancementDisplay(event);
        if (display == null) {
            return;
        }
        bridge.onPlayerAdvancement(player.getGameProfile().getName(), display.title(), display.description());
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
            RatBridgeNeoForge.LOGGER.error("RatBridge config reload failed", error);
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
            RatBridgeNeoForge.LOGGER.error("RatBridge config is invalid after reload; bridge will stay disabled: {}", errors);
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
                        new NeoForgeMinecraftMessageSink(server),
                        new NeoForgeServerStatusProvider(server, tpsMonitor),
                        () -> DiscordClientFactory.create(config)
                );
                if (!serverAvailable.get()) {
                    bridge.stop();
                    return;
                }
                RatBridgeNeoForge.LOGGER.info("RatBridge reloaded with client={} mode={}", config.client(), config.mode());
                server.execute(() -> source.sendSuccess(() -> Component.literal("RatBridge reloaded and Discord is connected."), false));
            } catch (Exception error) {
                bridge.stop();
                RatBridgeNeoForge.LOGGER.error("RatBridge failed to start after reload; bridge will stay disabled", error);
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

    private static AdvancementDisplay readAdvancementDisplay(Object event) {
        try {
            Object advancementHolder = invoke(event, "getAdvancement");
            Object advancement = invokeIfPresent(advancementHolder, "value");
            if (advancement == null) {
                advancement = advancementHolder;
            }

            Object display = invokeIfPresent(advancement, "display");
            if (display instanceof Optional<?> optional) {
                if (optional.isEmpty()) {
                    return null;
                }
                display = optional.get();
            }
            if (display == null) {
                display = invokeIfPresent(advancement, "getDisplay");
            }
            if (display == null) {
                return null;
            }

            Object announce = invokeIfPresent(display, "shouldAnnounceChat");
            if (announce instanceof Boolean shouldAnnounce && !shouldAnnounce) {
                return null;
            }

            Object title = firstPresent(display, "getTitle", "title");
            Object description = firstPresent(display, "getDescription", "description");
            return new AdvancementDisplay(componentString(title), componentString(description));
        } catch (ReflectiveOperationException | RuntimeException error) {
            RatBridgeNeoForge.LOGGER.warn("Unable to read NeoForge advancement event", error);
            return null;
        }
    }

    private static Object firstPresent(Object target, String firstMethod, String secondMethod) throws ReflectiveOperationException {
        Object value = invokeIfPresent(target, firstMethod);
        return value == null ? invoke(target, secondMethod) : value;
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invokeIfPresent(Object target, String methodName) throws ReflectiveOperationException {
        try {
            return invoke(target, methodName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String componentString(Object component) throws ReflectiveOperationException {
        Object value = invoke(component, "getString");
        return value instanceof String string ? string : "";
    }

    private record AdvancementDisplay(String title, String description) {
    }
}
