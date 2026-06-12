package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

    @Test
    void topicUpdaterFormatsStatusOnStartAndShutdown() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        BridgeConfig config = topicConfig();
        ServerStatusProvider statusProvider = uptimeMillis -> new ServerStatusSnapshot(
                3,
                20,
                11,
                "Rat SMP",
                "Forge-1.20.1",
                19.87,
                125_000L
        );

        controller.start(config, message -> { }, statusProvider, () -> client);

        assertEquals("topic-channel", client.topicChannelId.get(1, TimeUnit.SECONDS));
        assertEquals("3/20 online | TPS 19.87 | Rat SMP | up 2m", client.topic.get(1, TimeUnit.SECONDS));

        client.resetTopicFutures();
        controller.onServerStopping();

        assertEquals("topic-channel", client.topicChannelId.get(1, TimeUnit.SECONDS));
        assertEquals("Offline after 2m with 3 players cached", client.topic.get(1, TimeUnit.SECONDS));
        controller.stop();
    }

    @Test
    void channelNameUpdaterFormatsStatusOnStartAndShutdown() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        BridgeConfig config = channelNameConfig();
        ServerStatusProvider statusProvider = uptimeMillis -> new ServerStatusSnapshot(
                5,
                20,
                12,
                "Rat SMP",
                "Forge-1.20.1",
                19.5,
                600_000L
        );

        controller.start(config, message -> { }, statusProvider, () -> client);

        assertEquals("name-channel", client.nameChannelId.get(1, TimeUnit.SECONDS));
        assertEquals("5 players online", client.name.get(1, TimeUnit.SECONDS));

        client.resetNameFutures();
        controller.onServerStopping();

        assertEquals("name-channel", client.nameChannelId.get(1, TimeUnit.SECONDS));
        assertEquals("Server is offline", client.name.get(1, TimeUnit.SECONDS));
        controller.stop();
    }

    private static BridgeConfig config(boolean webhookDelivery) {
        return config(webhookDelivery, true, true);
    }

    private static BridgeConfig config(boolean webhookDelivery, boolean syncMinecraftToDiscordChat, boolean syncDiscordToMinecraftChat) {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                webhookDelivery, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 10,
                List.of(),
                true, syncMinecraftToDiscordChat, syncDiscordToMinecraftChat,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static BridgeConfig topicConfig() {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                true, "topic-channel", "%playercount%/%playermax% online | TPS %tps% | %motd% | up %uptimemins%m", "Offline after %uptimemins%m with %playercount% players cached", 10,
                List.of(),
                true, true, true,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static BridgeConfig channelNameConfig() {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 10,
                List.of(new ChannelNameUpdaterConfig("name-channel", "%playercount% players online", "Server is offline", 10)),
                true, true, true,
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
        private CompletableFuture<String> topicChannelId = new CompletableFuture<>();
        private CompletableFuture<String> topic = new CompletableFuture<>();
        private CompletableFuture<String> nameChannelId = new CompletableFuture<>();
        private CompletableFuture<String> name = new CompletableFuture<>();

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
        public CompletableFuture<Void> updateChannelTopic(String channelId, String topic) {
            this.topicChannelId.complete(channelId);
            this.topic.complete(topic);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateChannelName(String channelId, String name) {
            this.nameChannelId.complete(channelId);
            this.name.complete(name);
            return CompletableFuture.completedFuture(null);
        }

        private void resetTopicFutures() {
            topicChannelId = new CompletableFuture<>();
            topic = new CompletableFuture<>();
        }

        private void resetNameFutures() {
            nameChannelId = new CompletableFuture<>();
            name = new CompletableFuture<>();
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
