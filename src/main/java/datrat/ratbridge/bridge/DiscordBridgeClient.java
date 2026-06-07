package datrat.ratbridge.bridge;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface DiscordBridgeClient extends AutoCloseable {
    void start(BridgeConfig config, Consumer<DiscordInboundMessage> inboundConsumer) throws Exception;

    CompletableFuture<Void> sendMessage(String message);

    default void sendMessageBlocking(String message, Duration timeout) {
        try {
            sendMessage(message).get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Shutdown is best-effort; callers should not fail Minecraft server stop.
        }
    }

    @Override
    void close();
}
