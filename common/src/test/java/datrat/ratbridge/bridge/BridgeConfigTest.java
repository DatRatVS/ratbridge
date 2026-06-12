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
    void selfbotDoesNotRequireGuild() {
        BridgeConfig config = base("selfbot", "abc", "", "456", true);

        ValidationResult result = config.validate(emptyEnv());

        assertTrue(result.valid());
    }

    @Test
    void selfbotPollIntervalHasMinimum() {
        BridgeConfig config = new BridgeConfig(true, "discord", "selfbot", "abc", "", "", "456", true,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                List.of(),
                true, true, true, true, true, true, true, true, true,
                250,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
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
                List.of(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
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
                List.of(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
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
                List.of(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
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
                List.of(new ChannelNameUpdaterConfig("", "", "Server is offline", 4)),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");

        ValidationResult result = config.validate(emptyEnv());

        assertFalse(result.valid());
        assertTrue(result.errors().contains("channel name updaters require mode = 'bot'; Discord guild channels cannot be managed by selfbot mode"));
        assertTrue(result.errors().contains("channelNameUpdater1ChannelId is required"));
        assertTrue(result.errors().contains("channelNameUpdater1Message is required"));
        assertTrue(result.errors().contains("channelNameUpdater1UpdateInterval must be at least 5 to respect Discord rate limits"));
    }

    private static BridgeConfig base(String mode, String token, String serverId, String channelId, boolean enableSelfbot) {
        return new BridgeConfig(true, "discord", mode, token, "RATBRIDGE_DISCORD_TOKEN", serverId, channelId, enableSelfbot,
                false, "RatBridge",
                false, "", "Players: %playercount%/%playermax%", "Server is offline", 6,
                List.of(),
                true, true, true, true, true, true, true, true, true,
                750,
                "[MC] <{player}> {message}", "[Discord] <{author}> {message}", "[MC] {message}",
                "{player} joined the game", "{player} left the game", "{message}", "{player} has made the advancement [{advancement}]", "Server started", "Server stopping");
    }

    private static Function<String, String> emptyEnv() {
        return key -> null;
    }

    private static Function<String, String> env(Map<String, String> values) {
        return values::get;
    }
}
