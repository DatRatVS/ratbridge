package datrat.simplebridge.bridge;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class BridgeController {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DiscordBridgeClient client;
    private BridgeConfig config;
    private MinecraftMessageSink minecraftSink;

    public synchronized void start(BridgeConfig config, MinecraftMessageSink minecraftSink, Supplier<DiscordBridgeClient> clientFactory) throws Exception {
        stop();
        this.config = Objects.requireNonNull(config);
        this.minecraftSink = Objects.requireNonNull(minecraftSink);
        this.client = Objects.requireNonNull(clientFactory.get());
        this.client.start(config, this::onDiscordMessage);
        running.set(true);
    }

    public synchronized void stop() {
        running.set(false);
        if (client != null) {
            client.close();
            client = null;
        }
        minecraftSink = null;
        config = null;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void onMinecraftChat(String player, String message) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncChat()) {
            return;
        }
        String formatted = MessageFormatter.format(current.minecraftToDiscordFormat(), Map.of(
                "player", player,
                "message", message
        ));
        sendToDiscord(formatted);
    }

    public void onPlayerJoined(String player) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncPlayerJoin()) {
            return;
        }
        sendEvent(player + " joined the game");
    }

    public void onPlayerLeft(String player) {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncPlayerLeave()) {
            return;
        }
        sendEvent(player + " left the game");
    }

    public void onServerStarted() {
        BridgeConfig current = config;
        if (!isRunning() || current == null || !current.syncServerStart()) {
            return;
        }
        sendEvent("Server started");
    }

    public void onServerStopping() {
        BridgeConfig current = config;
        DiscordBridgeClient currentClient = client;
        if (!isRunning() || current == null || currentClient == null || !current.syncServerStop()) {
            return;
        }
        String formatted = MessageFormatter.format(current.eventFormat(), Map.of("message", "Server stopping"));
        currentClient.sendMessageBlocking(MentionSanitizer.sanitize(formatted), Duration.ofSeconds(5));
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
        MinecraftMessageSink sink = minecraftSink;
        if (!isRunning() || current == null || sink == null || !current.syncChat()) {
            return;
        }
        String formatted = MessageFormatter.format(current.discordToMinecraftFormat(), Map.of(
                "author", inbound.author(),
                "message", inbound.content()
        ));
        sink.sendSystemMessage(formatted);
    }
}
