package datrat.ratbridge.bridge;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface DiscordBridgeClient extends AutoCloseable {
    void start(BridgeConfig config, Consumer<DiscordInboundMessage> inboundConsumer) throws Exception;

    CompletableFuture<Void> sendMessage(String message);

    default CompletableFuture<Void> sendMinecraftChatMessage(String player, String message) {
        return sendMessage(message);
    }

    default CompletableFuture<Void> sendDirectMessage(String userId, String channelId, String message) {
        return sendMessage(message);
    }

    default CompletableFuture<Void> updateChannelTopic(String channelId, String topic) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletableFuture<Void> updateChannelName(String channelId, String name) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletableFuture<Void> updateBotPresence(BotPresenceConfig presence) {
        return CompletableFuture.completedFuture(null);
    }

    default void sendMessageBlocking(String message, Duration timeout) {
        try {
            sendMessage(message).get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Shutdown is best-effort; callers should not fail Minecraft server stop.
        }
    }

    default void updateChannelTopicBlocking(String channelId, String topic, Duration timeout) {
        try {
            updateChannelTopic(channelId, topic).get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Shutdown is best-effort; callers should not fail Minecraft server stop.
        }
    }

    default void updateChannelNameBlocking(String channelId, String name, Duration timeout) {
        try {
            updateChannelName(channelId, name).get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Shutdown is best-effort; callers should not fail Minecraft server stop.
        }
    }

    @Override
    void close();
}
