package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BridgeConfigFileTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndLoadsDefaultConfig() throws Exception {
        Path config = tempDir.resolve("ratbridge.toml");

        BridgeConfig loaded = BridgeConfigFile.load(config);

        assertTrue(Files.exists(config));
        assertEquals("discord", loaded.client());
        assertEquals("bot", loaded.mode());
        assertEquals("RATBRIDGE_DISCORD_TOKEN", loaded.tokenEnv());
        assertEquals(750, loaded.selfbotPollIntervalMillis());
    }

    @Test
    void parsesFlatTomlValuesWithComments() throws Exception {
        Path config = tempDir.resolve("ratbridge.toml");
        Files.writeString(config, """
                enabled = true # comment
                client = "discord"
                mode = "selfbot"
                token = "abc"
                tokenEnv = ""
                channelId = "42"
                enableSelfbot = true
                selfbotPollIntervalMillis = 500
                minecraftToDiscordFormat = "[MC] {message} # not comment"
                """);

        BridgeConfig loaded = BridgeConfigFile.load(config);

        assertEquals("selfbot", loaded.mode());
        assertEquals("42", loaded.channelId());
        assertEquals(500, loaded.selfbotPollIntervalMillis());
        assertEquals("[MC] {message} # not comment", loaded.minecraftToDiscordFormat());
    }
}
