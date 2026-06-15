package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BridgeConfigTest {
    @Test
    void botConfigRequiresGuildChannelAndToken() {
        BridgeConfig config = base("bot", "", "", "", false);

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("token is required; set token or tokenEnv"));
        assertTrue(result.errors().contains("serverId is required when mode is 'bot'"));
        assertTrue(result.errors().contains("channelId is required when mode is 'bot'"));
    }

    @Test
    void botConfigCanResolveTokenFromEnvironment() {
        BridgeConfig config = base("bot", "", "123", "456", false);

        ValidationResult result = config.validate(env(Map.of("RATBRIDGE_DISCORD_TOKEN", "abc")));

        assertTrue(result.valid());
    }

    @Test
    void selfbotRequiresExplicitOptIn() {
        BridgeConfig config = base("selfbot", "abc", "", "456", false);

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("enableSelfbot")));
    }

    @Test
    void selfbotCanOmitGuildUntilChannelTypeIsKnownAtRuntime() {
        BridgeConfig config = base("selfbot", "abc", "", "456", true);

        ValidationResult result = config.validate(emptyEnv());

        assertTrue(result.valid());
    }

    @Test
    void selfbotPollIntervalHasMinimum() {
        BridgeConfig config = new BridgeConfig(true, "discord", "selfbot", "abc", "", "", "456", true,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                250,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("selfbotPollIntervalMillis must be at least 500"));
    }

    @Test
    void webhookDeliveryRequiresBotMode() {
        BridgeConfig config = new BridgeConfig(true, "discord", "selfbot", "abc", "", "", "456", true,
                true, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("webhookDelivery requires mode = 'bot'; it is not allowed with selfbot mode"));
    }

    @Test
    void unsupportedClientAndModeAreInvalid() {
        BridgeConfig config = new BridgeConfig(true, "slack", "webhook", "abc", "", "", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("client must be 'discord'"));
        assertTrue(result.errors().contains("mode must be 'bot' or 'selfbot'"));
    }

    @Test
    void topicUpdaterRequiresBotModeAndRateLimitSafeInterval() {
        BridgeConfig config = new BridgeConfig(true, "discord", "selfbot", "abc", "", "", "456", true,
                false, "RatBridge",
                true, "", "Players: %playercount%/%playermax%", "Server is offline", 4,
                false,
                List.of(),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("topicUpdaterEnabled requires mode = 'bot'; Discord channel topics cannot be managed by selfbot mode"));
        assertTrue(result.errors().contains("topicUpdaterIntervalMinutes must be at least 5 to respect Discord rate limits"));
    }

    @Test
    void channelNameUpdatersRequireBotModeAndRateLimitSafeInterval() {
        BridgeConfig config = new BridgeConfig(true, "discord", "selfbot", "abc", "", "", "456", true,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                true,
                List.of(new ChannelNameUpdaterConfig("", "", "Server is offline", 4)),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("channel name updaters require mode = 'bot'; Discord guild channels cannot be managed by selfbot mode"));
        assertTrue(result.errors().contains("channelNameUpdater1ChannelId is required"));
        assertTrue(result.errors().contains("channelNameUpdater1Message is required"));
        assertTrue(result.errors().contains("channelNameUpdater1UpdateInterval must be at least 5 to respect Discord rate limits"));
    }

    @Test
    void disabledChannelNameUpdatersAreIgnoredDuringValidation() {
        BridgeConfig config = new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(new ChannelNameUpdaterConfig("", "", "Server is offline", 1)),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertTrue(result.valid());
    }

    @Test
    void botPresenceRequiresBotModeAndRateLimitSafeInterval() {
        BridgeConfig config = new BridgeConfig(true, "discord", "selfbot", "abc", "", "", "456", true,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                true,
                List.of(new BotPresenceConfig("invalid", "invalid", "online", "", 10)),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("bot presence updates require mode = 'bot'; Discord selfbot mode cannot use gateway bot presence"));
        assertTrue(result.errors().contains("botPresence1OnlineStatus must be one of ONLINE, IDLE, AWAY, DND, DO_NOT_DISTURB, INVISIBLE"));
        assertTrue(result.errors().contains("botPresence1ActivityType must be one of PLAYING, LISTENING, WATCHING, STREAMING, COMPETING, CUSTOM"));
        assertTrue(result.errors().contains("botPresence1UpdateInterval must be at least 30 seconds to respect Discord presence rate limits"));
    }

    @Test
    void streamingPresenceRequiresStreamUrl() {
        BridgeConfig config = new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                true,
                List.of(new BotPresenceConfig("online", "streaming", "RatBridge", "", 60)),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("botPresence1StreamUrl is required when ActivityType is STREAMING"));
    }

    @Test
    void disabledBotPresenceUpdatesAreIgnoredDuringValidation() {
        BridgeConfig config = new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(new BotPresenceConfig("invalid", "invalid", "online", "", 1)),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertTrue(result.valid());
    }

    @Test
    void authenticationRequiresUsableMessagesAndTtl() {
        BridgeConfig config = new BridgeConfig(true, "discord", "bot", "abc", "", "123", "456", false,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(),
                new AuthenticationConfig(true, 0, true, "", "", "", "bad", "linked", "", "out", "none"),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("authenticationCodeTtlMinutes must be at least 1"));
        assertTrue(result.errors().contains("authenticationKickMessage is required when authentication is enabled"));
        assertTrue(result.errors().contains("authenticationSuccessMessage is required when authentication is enabled"));
        assertTrue(result.errors().contains("authenticationLogoutCommand is required when authentication is enabled"));
        assertTrue(result.errors().contains("authenticationUnauthenticatedLoginMessage is required when authenticationUnauthenticatedLoginMessageEnabled is true"));
    }

    private static BridgeConfig base(String mode, String token, String serverId, String channelId, boolean enableSelfbot) {
        return new BridgeConfig(true, "discord", mode, token, "RATBRIDGE_DISCORD_TOKEN", serverId, channelId, enableSelfbot,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                false,
                List.of(),
                false,
                List.of(),
                AuthenticationConfig.disabled(),
                DiscordCommandConfig.defaults(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[Discord] <{author}> replied to <{replyAuthor}>: {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static Function<String, String> emptyEnv() {
        return key -> null;
    }

    private static Function<String, String> env(Map<String, String> values) {
        return values::get;
    }
}
