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
    void writesAndLoadsDefaultSplitConfig() throws Exception {
        Path configDir = tempDir.resolve("ratbridge");

        BridgeConfig loaded = BridgeConfigFile.loadSplit(configDir);

        assertTrue(Files.exists(configDir.resolve("config.toml")));
        assertTrue(Files.exists(configDir.resolve("messages.toml")));
        assertEquals("discord", loaded.client());
        assertEquals("bot", loaded.mode());
        assertEquals("RATBRIDGE_DISCORD_TOKEN", loaded.tokenEnv());
        assertEquals(750, loaded.selfbotPollIntervalMillis());
        assertEquals("Server stopping", loaded.serverStopMessage());
    }

    @Test
    void parsesSplitTomlValuesWithComments() throws Exception {
        Path configDir = tempDir.resolve("ratbridge");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.toml"), """
                enabled = true # comment
                client = "discord"
                mode = "selfbot"
                token = "abc"
                tokenEnv = ""
                channelId = "42"
                enableSelfbot = true
                selfbotPollIntervalMillis = 500
                """);
        Files.writeString(configDir.resolve("messages.toml"), """
                minecraftToDiscordFormat = "[MC] {message} # not comment"
                playerJoinMessage = "{player} entrou no jogo"
                """);

        BridgeConfig loaded = BridgeConfigFile.loadSplit(configDir);

        assertEquals("selfbot", loaded.mode());
        assertEquals("42", loaded.channelId());
        assertEquals(500, loaded.selfbotPollIntervalMillis());
        assertEquals("[MC] {message} # not comment", loaded.minecraftToDiscordFormat());
        assertEquals("{player} entrou no jogo", loaded.playerJoinMessage());
    }

    @Test
    void migratesLegacyConfigIntoSplitFiles() throws Exception {
        Path legacy = tempDir.resolve("ratbridge.toml");
        Files.writeString(legacy, """
                mode = "selfbot"
                token = "abc"
                channelId = "42"
                enableSelfbot = true
                syncPlayerJoin = false
                playerJoinMessage = "{player} chegou"
                """);

        BridgeConfig loaded = BridgeConfigFile.loadSplit(tempDir.resolve("ratbridge"));

        assertEquals("selfbot", loaded.mode());
        assertEquals("42", loaded.channelId());
        assertEquals(false, loaded.syncPlayerJoin());
        assertEquals("{player} chegou", loaded.playerJoinMessage());
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("config.toml")).contains("mode = \"selfbot\""));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("messages.toml")).contains("syncPlayerJoin = false"));
    }
}
