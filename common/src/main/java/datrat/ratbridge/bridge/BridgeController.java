package datrat.ratbridge.bridge;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BridgeController {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DiscordBridgeClient client;
    private BridgeConfig config;
    private MinecraftMessageSink minecraftSink;
    private ServerStatusProvider statusProvider = ServerStatusProvider.empty();
    private ScheduledExecutorService topicUpdater;
    private ScheduledExecutorService channelNameUpdater;
    private ScheduledExecutorService botPresenceUpdater;
    private final AuthenticationService authenticationService = new AuthenticationService();
    private Consumer<AuthenticationLogout> authenticationLogoutConsumer = logout -> { };
    private long startedAtMillis;

    public synchronized void start(BridgeConfig config, MinecraftMessageSink minecraftSink, Supplier<DiscordBridgeClient> clientFactory) throws Exception {
        start(config, minecraftSink, ServerStatusProvider.empty(), clientFactory);
    }

    public synchronized void start(
            BridgeConfig config,
            MinecraftMessageSink minecraftSink,
            ServerStatusProvider statusProvider,
            Supplier<DiscordBridgeClient> clientFactory
    ) throws Exception {
        start(config, minecraftSink, statusProvider, clientFactory, null);
    }

    public synchronized void start(
            BridgeConfig config,
            MinecraftMessageSink minecraftSink,
            ServerStatusProvider statusProvider,
            Supplier<DiscordBridgeClient> clientFactory,
            AuthenticationStore authenticationStore
    ) throws Exception {
        start(config, minecraftSink, statusProvider, clientFactory, authenticationStore, logout -> { });
    }

    public synchronized void start(
            BridgeConfig config,
            MinecraftMessageSink minecraftSink,
            ServerStatusProvider statusProvider,
            Supplier<DiscordBridgeClient> clientFactory,
            AuthenticationStore authenticationStore,
            Consumer<AuthenticationLogout> authenticationLogoutConsumer
    ) throws Exception {
        stop();
        this.config = Objects.requireNonNull(config);
        this.minecraftSink = Objects.requireNonNull(minecraftSink);
        this.statusProvider = Objects.requireNonNull(statusProvider);
        this.authenticationLogoutConsumer = Objects.requireNonNull(authenticationLogoutConsumer);
        if (config.authentication().enabled()) {
            authenticationService.start(Objects.requireNonNull(authenticationStore, "authenticationStore"));
        }
        this.client = Objects.requireNonNull(clientFactory.get());
        this.client.start(config, this::onDiscordMessage);
        this.startedAtMillis = System.currentTimeMillis();
        running.set(true);
        startTopicUpdater();
        startChannelNameUpdater();
        startBotPresenceUpdater();
    }

    public synchronized void stop() {
        running.set(false);
        stopTopicUpdater();
        stopChannelNameUpdater();
        stopBotPresenceUpdater();
        authenticationService.stop();
        if (client != null) {
            client.close();
            client = null;
        }
        minecraftSink = null;
        config = null;
        statusProvider = ServerStatusProvider.empty();
        authenticationLogoutConsumer = logout -> { };
        startedAtMillis = 0L;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void onMinecraftChat(String player, String message) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncChat() || !current.syncMinecraftToDiscordChat()) {
            return;
        }
        String formatted = MessageFormatter.format(current.minecraftToDiscordFormat(), Map.of(
                "player", player,
                "message", message
        ));
        DiscordBridgeClient currentClient = client;
        if (currentClient == null) {
            return;
        }
        if (current.webhookDelivery()) {
            currentClient.sendMinecraftChatMessage(player, MentionSanitizer.sanitize(message));
            return;
        }
        currentClient.sendMessage(MentionSanitizer.sanitize(formatted));
    }

    public void onPlayerJoined(String player) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncPlayerJoin()) {
            return;
        }
        sendEvent(MessageFormatter.format(current.playerJoinMessage(), Map.of("player", player)));
    }

    public AuthenticationDecision authenticateLogin(String playerUuid, String player) {
        BridgeConfig current = config;
        if (!isRunning() || current == null) {
            return AuthenticationDecision.allow();
        }
        return authenticationService.checkLogin(current, playerUuid, player);
    }

    public void onPlayerLeft(String player) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncPlayerLeave()) {
            return;
        }
        sendEvent(MessageFormatter.format(current.playerLeaveMessage(), Map.of("player", player)));
    }

    public void onPlayerDied(String player, String deathMessage) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncPlayerDeath()) {
            return;
        }
        sendEvent(MessageFormatter.format(current.playerDeathMessage(), Map.of(
                "player", player,
                "message", deathMessage
        )));
    }

    public void onPlayerAdvancement(String player, String advancement, String description) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncPlayerAdvancement()) {
            return;
        }
        sendEvent(MessageFormatter.format(current.playerAdvancementMessage(), Map.of(
                "player", player,
                "advancement", advancement,
                "description", description
        )));
    }

    public void onServerStarted() {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncServerStart()) {
            return;
        }
        sendEvent(current.serverStartMessage());
    }

    public void onServerStopping() {
        BridgeConfig current = config;
        DiscordBridgeClient currentClient = client;
        if (!isRunning() || current == null || currentClient == null) {
            return;
        }
        if (current.syncServerStop()) {
            String formatted = MessageFormatter.format(current.eventFormat(), Map.of("message", current.serverStopMessage()));
            currentClient.sendMessageBlocking(MentionSanitizer.sanitize(formatted), Duration.ofSeconds(5));
        }
        updateShutdownTopic(current, currentClient);
        updateShutdownChannelNames(current, currentClient);
    }

    private void sendEvent(String eventMessage) {
        BridgeConfig current = config;
        if (current == null) {
            return;
        }
        String formatted = MessageFormatter.format(current.eventFormat(), Map.of("message", eventMessage));
        sendToDiscord(formatted);
    }

    private void sendToDiscord(String message) {
        DiscordBridgeClient currentClient = client;
        if (currentClient != null) {
            currentClient.sendMessage(MentionSanitizer.sanitize(message));
        }
    }

    private void onDiscordMessage(DiscordInboundMessage inbound) {
        BridgeConfig current = config;
        DiscordBridgeClient currentClient = client;
        if (current != null && handleDiscordCommand(current, currentClient, inbound)) {
            return;
        }
        if (current != null && current.authentication().enabled() && inbound.privateMessage()) {
            java.util.Optional<AuthenticationMessageResponse> response = authenticationService.handlePrivateMessage(current, inbound);
            if (response.isPresent()) {
                AuthenticationMessageResponse authenticationResponse = response.get();
                if (currentClient != null && BridgeConfig.hasText(authenticationResponse.replyMessage())) {
                    currentClient.sendDirectMessage(inbound.authorId(), inbound.channelId(), authenticationResponse.replyMessage());
                }
                authenticationResponse.logout().ifPresent(authenticationLogoutConsumer);
                return;
            }
            if (!inbound.bridgeToMinecraft()) {
                return;
            }
        }

        MinecraftMessageSink sink = minecraftSink;
        if (!isRunning() || current == null || sink == null || !inbound.bridgeToMinecraft() || !current.syncChat() || !current.syncDiscordToMinecraftChat()) {
            return;
        }
        String template = inbound.hasReplyAuthor() ? current.discordReplyToMinecraftFormat() : current.discordToMinecraftFormat();
        String formatted = MessageFormatter.format(template, Map.of(
                "author", inbound.author(),
                "replyAuthor", inbound.replyAuthor(),
                "replyauthor", inbound.replyAuthor(),
                "message", inbound.content()
        ));
        sink.sendSystemMessage(formatted);
    }

    private boolean handleDiscordCommand(BridgeConfig current, DiscordBridgeClient currentClient, DiscordInboundMessage inbound) {
        if (!isRunning() || currentClient == null || !current.discordCommands().enabled()) {
            return false;
        }
        if (!inbound.channelId().equals(current.channelId())) {
            return false;
        }
        if (!inbound.content().trim().equalsIgnoreCase(current.discordCommands().onlineCommand().trim())) {
            return false;
        }

        currentClient.sendTemporaryMessage(
                inbound.channelId(),
                MentionSanitizer.sanitize(onlinePlayersMessage(current)),
                Duration.ofSeconds(Math.max(1, current.discordCommands().onlineDeleteAfterSeconds()))
        );
        return true;
    }

    private String onlinePlayersMessage(BridgeConfig current) {
        List<String> players = statusProvider.onlinePlayerNames();
        int playerCount = players.size();
        String template = playerCount == 0
                ? current.discordCommands().onlineNoPlayersMessage()
                : current.discordCommands().onlinePlayersMessage();
        return MessageFormatter.format(template, Map.of(
                "playercount", Integer.toString(playerCount),
                "playerPlural", playerCount == 1 ? "" : "s",
                "playerplural", playerCount == 1 ? "" : "s",
                "players", String.join(", ", players)
        ));
    }

    private synchronized void startTopicUpdater() {
        BridgeConfig current = config;
        if (current == null || !current.topicUpdaterEnabled()) {
            return;
        }
        topicUpdater = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RatBridge-Topic-Updater");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMinutes = Math.max(5, current.topicUpdaterIntervalMinutes());
        topicUpdater.scheduleWithFixedDelay(this::updateTopicSafely, 0L, intervalMinutes, TimeUnit.MINUTES);
    }

    private synchronized void stopTopicUpdater() {
        if (topicUpdater != null) {
            topicUpdater.shutdownNow();
            topicUpdater = null;
        }
    }

    private void updateTopicSafely() {
        try {
            BridgeConfig current = config;
            DiscordBridgeClient currentClient = client;
            if (!isRunning() || current == null || currentClient == null || !current.topicUpdaterEnabled()) {
                return;
            }
            currentClient.updateChannelTopic(
                    current.resolvedTopicUpdaterChannelId(),
                    buildTopic(current.topicUpdaterMessage())
            );
        } catch (Exception ignored) {
            // Discord client implementations log REST failures; the scheduler must keep running.
        }
    }

    private void updateShutdownTopic(BridgeConfig current, DiscordBridgeClient currentClient) {
        if (!current.topicUpdaterEnabled() || !BridgeConfig.hasText(current.topicUpdaterShutdownMessage())) {
            return;
        }
        currentClient.updateChannelTopicBlocking(
                current.resolvedTopicUpdaterChannelId(),
                buildTopic(current.topicUpdaterShutdownMessage()),
                Duration.ofSeconds(5)
        );
    }

    private String buildTopic(String template) {
        long uptimeMillis = startedAtMillis == 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAtMillis);
        return buildStatusMessage(template, uptimeMillis);
    }

    private synchronized void startChannelNameUpdater() {
        BridgeConfig current = config;
        if (current == null || !current.channelNameUpdatersEnabled() || current.channelNameUpdaters().isEmpty()) {
            return;
        }
        channelNameUpdater = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RatBridge-Channel-Name-Updater");
            thread.setDaemon(true);
            return thread;
        });
        for (ChannelNameUpdaterConfig updater : current.channelNameUpdaters()) {
            long intervalMinutes = Math.max(5, updater.updateIntervalMinutes());
            channelNameUpdater.scheduleWithFixedDelay(
                    () -> updateChannelNameSafely(updater),
                    0L,
                    intervalMinutes,
                    TimeUnit.MINUTES
            );
        }
    }

    private synchronized void stopChannelNameUpdater() {
        if (channelNameUpdater != null) {
            channelNameUpdater.shutdownNow();
            channelNameUpdater = null;
        }
    }

    private void updateChannelNameSafely(ChannelNameUpdaterConfig updater) {
        try {
            BridgeConfig current = config;
            DiscordBridgeClient currentClient = client;
            if (!isRunning() || current == null || currentClient == null || !current.channelNameUpdatersEnabled() || !current.channelNameUpdaters().contains(updater)) {
                return;
            }
            currentClient.updateChannelName(updater.channelId(), buildStatusMessage(updater.message()));
        } catch (Exception ignored) {
            // Discord client implementations log REST failures; the scheduler must keep running.
        }
    }

    private void updateShutdownChannelNames(BridgeConfig current, DiscordBridgeClient currentClient) {
        if (!current.channelNameUpdatersEnabled()) {
            return;
        }
        for (ChannelNameUpdaterConfig updater : current.channelNameUpdaters()) {
            if (!BridgeConfig.hasText(updater.shutdownMessage())) {
                continue;
            }
            currentClient.updateChannelNameBlocking(
                    updater.channelId(),
                    buildStatusMessage(updater.shutdownMessage()),
                    Duration.ofSeconds(5)
            );
        }
    }

    private synchronized void startBotPresenceUpdater() {
        BridgeConfig current = config;
        if (current == null || !current.botPresenceEnabled() || current.botPresenceUpdates().isEmpty()) {
            return;
        }
        botPresenceUpdater = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RatBridge-Bot-Presence-Updater");
            thread.setDaemon(true);
            return thread;
        });
        botPresenceUpdater.execute(() -> updateBotPresenceSafely(0));
    }

    private synchronized void stopBotPresenceUpdater() {
        if (botPresenceUpdater != null) {
            botPresenceUpdater.shutdownNow();
            botPresenceUpdater = null;
        }
    }

    private void updateBotPresenceSafely(int index) {
        try {
            BridgeConfig current = config;
            DiscordBridgeClient currentClient = client;
            ScheduledExecutorService scheduler = botPresenceUpdater;
            if (!isRunning() || current == null || currentClient == null || scheduler == null || !current.botPresenceEnabled() || current.botPresenceUpdates().isEmpty()) {
                return;
            }

            int safeIndex = Math.floorMod(index, current.botPresenceUpdates().size());
            BotPresenceConfig presence = current.botPresenceUpdates().get(safeIndex);
            currentClient.updateBotPresence(new BotPresenceConfig(
                    presence.onlineStatus(),
                    presence.activityType(),
                    buildStatusMessage(presence.activity()),
                    presence.streamUrl(),
                    presence.updateIntervalSeconds()
            ));

            int nextIndex = (safeIndex + 1) % current.botPresenceUpdates().size();
            long delaySeconds = Math.max(30, presence.updateIntervalSeconds());
            scheduler.schedule(() -> updateBotPresenceSafely(nextIndex), delaySeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Discord client implementations log presence failures; the scheduler must keep running.
        }
    }

    private String buildStatusMessage(String template) {
        long uptimeMillis = startedAtMillis == 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAtMillis);
        return buildStatusMessage(template, uptimeMillis);
    }

    private String buildStatusMessage(String template, long uptimeMillis) {
        return TopicTemplateFormatter.format(template, statusProvider.snapshot(uptimeMillis));
    }
}
