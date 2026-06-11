package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void playerDeathAndAdvancementUseEditableEventMessages() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();

        controller.start(config(false), message -> { }, () -> client);
        controller.onPlayerDied("Steve", "Steve fell from a high place");
        assertEquals("[MC] Steve fell from a high place", client.normalMessage);

        controller.onPlayerAdvancement("Alex", "Stone Age", "Mine stone with your new pickaxe");
        assertEquals("[MC] Alex has made the advancement [Stone Age]", client.normalMessage);
    }

    @Test
    void canDisableMinecraftToDiscordChatWithoutDisablingDiscordToMinecraft() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        CapturingMinecraftSink sink = new CapturingMinecraftSink();

        controller.start(config(false, false, true), sink, () -> client);
        controller.onMinecraftChat("Steve", "hidden from discord");
        client.receive(new DiscordInboundMessage("Alex", "visible in minecraft"));

        assertNull(client.normalMessage);
        assertEquals("[Discord] <Alex> visible in minecraft", sink.message);
    }

    @Test
    void canDisableDiscordToMinecraftChatWithoutDisablingMinecraftToDiscord() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        CapturingMinecraftSink sink = new CapturingMinecraftSink();

        controller.start(config(false, true, false), sink, () -> client);
        controller.onMinecraftChat("Steve", "visible in discord");
        client.receive(new DiscordInboundMessage("Alex", "hidden from minecraft"));

        assertEquals("[MC] <Steve> visible in discord", client.normalMessage);
        assertNull(sink.message);
    }

    private static BridgeConfig config(boolean webhookDelivery) {
        return config(webhookDelivery, true, true);
    }

    private static BridgeConfig config(boolean webhookDelivery, boolean syncMinecraftToDiscordChat, boolean syncDiscordToMinecraftChat) {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                webhookDelivery, "RatBridge",
                true, syncMinecraftToDiscordChat, syncDiscordToMinecraftChat,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static final class FakeDiscordClient implements DiscordBridgeClient {
        private String normalMessage;
        private String webhookPlayer;
        private String webhookMessage;
        private Consumer<DiscordInboundMessage> inboundConsumer;

        @Override
        public void start(BridgeConfig config, Consumer<DiscordInboundMessage> inboundConsumer) {
            this.inboundConsumer = inboundConsumer;
        }

        private void receive(DiscordInboundMessage message) {
            inboundConsumer.accept(message);
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

    private static final class CapturingMinecraftSink implements MinecraftMessageSink {
        private String message;

        @Override
        public void sendSystemMessage(String message) {
            this.message = message;
        }
    }
}
