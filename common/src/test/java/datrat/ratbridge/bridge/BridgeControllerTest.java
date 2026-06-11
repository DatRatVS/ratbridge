package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BridgeControllerTest {
    @Test
    void webhookDeliveryMirrorsMinecraftChatButKeepsEventsNormal() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        BridgeConfig config = config(true);

        controller.start(config, message -> { }, () -> client);
        controller.onMinecraftChat("Steve", "hello @everyone");
        controller.onPlayerJoined("Alex");

        assertEquals("Steve", client.webhookPlayer);
        assertEquals("hello @\u200Beveryone", client.webhookMessage);
        assertEquals("[MC] Alex joined the game", client.normalMessage);
    }

    @Test
    void normalDeliveryUsesMinecraftToDiscordFormat() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();

        controller.start(config(false), message -> { }, () -> client);
        controller.onMinecraftChat("Steve", "hello");

        assertEquals("[MC] <Steve> hello", client.normalMessage);
    }

    private static BridgeConfig config(boolean webhookDelivery) {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                webhookDelivery, "RatBridge",
                true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "Server started", "Server stopping");
    }

    private static final class FakeDiscordClient implements DiscordBridgeClient {
        private String normalMessage;
        private String webhookPlayer;
        private String webhookMessage;

        @Override
        public void start(BridgeConfig config, Consumer<DiscordInboundMessage> inboundConsumer) {
        }

        @Override
        public CompletableFuture<Void> sendMessage(String message) {
            normalMessage = message;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> sendMinecraftChatMessage(String player, String message) {
            webhookPlayer = player;
            webhookMessage = message;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }
}
