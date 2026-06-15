package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BridgeControllerTest {
    @TempDir
    Path tempDir;

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
    void discordReplyUsesReplyFormatInMinecraft() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        CapturingMinecraftSink sink = new CapturingMinecraftSink();

        controller.start(config(false), sink, () -> client);
        client.receive(new DiscordInboundMessage("Alex", "that one", "Steve"));

        assertEquals("[Discord] <Alex> replied to <Steve>: that one", sink.message);
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

    @Test
    void botPresenceFormatsStatusOnStart() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        BridgeConfig config = presenceConfig();
        ServerStatusProvider statusProvider = uptimeMillis -> new ServerStatusSnapshot(
                7,
                20,
                18,
                "Rat SMP",
                "Forge-1.20.1",
                19.95,
                60_000L
        );

        controller.start(config, message -> { }, statusProvider, () -> client);

        BotPresenceConfig presence = client.presence.get(1, TimeUnit.SECONDS);
        assertEquals("DND", presence.onlineStatus());
        assertEquals("WATCHING", presence.activityType());
        assertEquals("7/20 players | TPS 19.95", presence.activity());
        assertEquals(30, presence.updateIntervalSeconds());
        controller.stop();
    }

    @Test
    void authenticationLinksAndLogsOutThroughPrivateDiscordMessages() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        List<AuthenticationLogout> logouts = new ArrayList<>();

        controller.start(
                authConfig(),
                message -> { },
                ServerStatusProvider.empty(),
                () -> client,
                new AuthenticationStore(tempDir.resolve("authentication-users.toml")),
                logouts::add
        );

        AuthenticationDecision firstJoin = controller.authenticateLogin("minecraft-uuid", "Steve");
        assertFalse(firstJoin.allowed());
        String code = authCode(firstJoin.disconnectMessage());

        client.receive(new DiscordInboundMessage("Alex", code, "", "discord-1", "dm-1", true, false));
        assertEquals("Authenticated Steve. You can now join the server.", client.directMessage);

        AuthenticationDecision secondJoin = controller.authenticateLogin("minecraft-uuid", "Steve");
        assertTrue(secondJoin.allowed());

        client.receive(new DiscordInboundMessage("Alex", "r!logout", "", "discord-1", "dm-1", true, false));
        assertEquals("Your Minecraft account link was removed. Join the server again to get a new code.", client.directMessage);
        assertEquals(1, logouts.size());
        assertEquals("minecraft-uuid", logouts.get(0).minecraftUuid());
        assertEquals("Steve", logouts.get(0).minecraftName());
        assertEquals("Your Minecraft account link was removed. Join the server again to get a new code.", logouts.get(0).disconnectMessage());

        AuthenticationDecision thirdJoin = controller.authenticateLogin("minecraft-uuid", "Steve");
        assertFalse(thirdJoin.allowed());
        controller.stop();
    }

    @Test
    void onlineCommandRepliesTemporarilyWithOnlinePlayers() throws Exception {
        BridgeController controller = new BridgeController();
        FakeDiscordClient client = new FakeDiscordClient();
        ServerStatusProvider statusProvider = new ServerStatusProvider() {
            @Override
            public ServerStatusSnapshot snapshot(long uptimeMillis) {
                return ServerStatusProvider.empty().snapshot(uptimeMillis);
            }

            @Override
            public List<String> onlinePlayerNames() {
                return List.of("Steve", "Alex");
            }
        };

        controller.start(config(false), message -> { }, statusProvider, () -> client);
        client.receive(new DiscordInboundMessage("DiscordUser", "r!online", "", "discord-1", "456", false, true));

        assertEquals("456", client.temporaryChannelId);
        assertEquals("**2 online players:** Steve, Alex", client.temporaryMessage);
        assertEquals(10, client.temporaryDeleteAfterSeconds);
        assertNull(client.normalMessage);
        controller.stop();
    }

    private static BridgeConfig config(boolean webhookDelivery) {
        return config(webhookDelivery, true, true);
    }

    private static BridgeConfig config(boolean webhookDelivery, boolean syncMinecraftToDiscordChat, boolean syncDiscordToMinecraftChat) {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                webhookDelivery, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, syncMinecraftToDiscordChat, syncDiscordToMinecraftChat,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static BridgeConfig topicConfig() {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                true, "topic-channel", "%playercount%/%playermax% online | TPS %tps% | %motd% | up %uptimemins%m", "Offline after %uptimemins%m with %playercount% players cached", 10,
                false,
                List.of(),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static BridgeConfig channelNameConfig() {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                true,
                List.of(new ChannelNameUpdaterConfig("name-channel", "%playercount% players online", "Server is offline", 6)),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static BridgeConfig presenceConfig() {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                true,
                List.of(new BotPresenceConfig("DND", "WATCHING", "%playercount%/%playermax% players | TPS %tps%", "", 30)),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static BridgeConfig authConfig() {
        return new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(),
                new AuthenticationConfig(
                        true,
                        10,
                        "Code {code} for {player}",
                        "Authenticated {player}. You can now join the server.",
                        "Invalid or expired authentication code.",
                        "That Minecraft or Discord account is already linked to another account.",
                        "r!logout",
                        "Your Minecraft account link was removed. Join the server again to get a new code.",
                        "Your Discord account is not linked to any Minecraft account."
                ),
                DiscordCommandConfig.defaults(),
                true, true, true,
                true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static String authCode(String message) {
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(message);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static final class FakeDiscordClient implements DiscordBridgeClient {
        private String normalMessage;
        private String webhookPlayer;
        private String webhookMessage;
        private String directMessage;
        private String temporaryChannelId;
        private String temporaryMessage;
        private long temporaryDeleteAfterSeconds;
        private Consumer<DiscordInboundMessage> inboundConsumer;
        private CompletableFuture<String> topicChannelId = new CompletableFuture<>();
        private CompletableFuture<String> topic = new CompletableFuture<>();
        private CompletableFuture<String> nameChannelId = new CompletableFuture<>();
        private CompletableFuture<String> name = new CompletableFuture<>();
        private CompletableFuture<BotPresenceConfig> presence = new CompletableFuture<>();

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
        public CompletableFuture<Void> sendDirectMessage(String userId, String channelId, String message) {
            directMessage = message;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> sendTemporaryMessage(String channelId, String message, java.time.Duration deleteAfter) {
            temporaryChannelId = channelId;
            temporaryMessage = message;
            temporaryDeleteAfterSeconds = deleteAfter.toSeconds();
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

        @Override
        public CompletableFuture<Void> updateBotPresence(BotPresenceConfig presence) {
            this.presence.complete(presence);
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
