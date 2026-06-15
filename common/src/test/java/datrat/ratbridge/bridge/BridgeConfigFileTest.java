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
        assertTrue(Files.exists(configDir.resolve("topic-updater.toml")));
        assertTrue(Files.exists(configDir.resolve("channel-updaters.toml")));
        assertTrue(Files.exists(configDir.resolve("bot-presence.toml")));
        assertTrue(Files.exists(configDir.resolve("authentication.toml")));
        assertEquals("discord", loaded.client());
        assertEquals("bot", loaded.mode());
        assertEquals("RATBRIDGE_DISCORD_TOKEN", loaded.tokenEnv());
        assertEquals(750, loaded.selfbotPollIntervalMillis());
        assertEquals(false, loaded.webhookDelivery());
        assertEquals("RatBridge", loaded.webhookName());
        assertEquals(false, loaded.topicUpdaterEnabled());
        assertEquals("", loaded.topicUpdaterChannelId());
        assertEquals("Players: %playercount%/%playermax% | TPS: %tps% | Uptime: %uptimemins%m", loaded.topicUpdaterMessage());
        assertEquals("Server is offline", loaded.topicUpdaterShutdownMessage());
        assertEquals(6, loaded.topicUpdaterIntervalMinutes());
        assertEquals(false, loaded.channelNameUpdatersEnabled());
        assertEquals(0, loaded.channelNameUpdaters().size());
        assertEquals(false, loaded.botPresenceEnabled());
        assertEquals(0, loaded.botPresenceUpdates().size());
        assertEquals(false, loaded.authentication().enabled());
        assertEquals(10, loaded.authentication().codeTtlMinutes());
        assertEquals(true, loaded.authentication().unauthenticatedLoginMessageEnabled());
        assertEquals("%player% tried to join but is not authenticated yet.", loaded.authentication().unauthenticatedLoginMessage());
        assertEquals("r!logout", loaded.authentication().logoutCommand());
        assertEquals(true, loaded.discordCommands().enabled());
        assertEquals("r!online", loaded.discordCommands().onlineCommand());
        assertEquals(10, loaded.discordCommands().onlineDeleteAfterSeconds());
        assertEquals("**%playercount% online player%playerplural%:** %players%", loaded.discordCommands().onlinePlayersMessage());
        assertEquals("**No online players.**", loaded.discordCommands().onlineNoPlayersMessage());
        assertEquals(true, loaded.syncMinecraftToDiscordChat());
        assertEquals(true, loaded.syncDiscordToMinecraftChat());
        assertEquals("[Discord] <%author%> replied to <%replyauthor%>: %message%", loaded.discordReplyToMinecraftFormat());
        assertEquals(true, loaded.syncPlayerDeath());
        assertEquals(true, loaded.syncPlayerAdvancement());
        assertEquals("%message%", loaded.playerDeathMessage());
        assertEquals("%player% has made the advancement [%advancement%]", loaded.playerAdvancementMessage());
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
                webhookDelivery = true
                webhookName = "RatBridge Chat"
                """);
        Files.writeString(configDir.resolve("topic-updater.toml"), """
                topicUpdaterEnabled = true
                topicUpdaterChannelId = "99"
                topicUpdaterMessage = "%playercount% online"
                topicUpdaterShutdownMessage = "offline"
                topicUpdaterIntervalMinutes = 15
                """);
        Files.writeString(configDir.resolve("channel-updaters.toml"), """
                channelNameUpdatersEnabled = true
                
                [[ChannelUpdater]]
                ChannelId = "100"
                Message = "%playercount% players"
                ShutdownMessage = "offline"
                UpdateInterval = 6
                
                [[ChannelUpdater]]
                ChannelId = "101"
                Message = "TPS %tps% # not comment"
                ShutdownMessage = "offline"
                UpdateInterval = 20
                """);
        Files.writeString(configDir.resolve("bot-presence.toml"), """
                botPresenceEnabled = true
                
                [[Presence]]
                OnlineStatus = "DND"
                ActivityType = "WATCHING"
                Activity = "%playercount% online # not comment"
                UpdateInterval = 30
                
                [[Presence]]
                OnlineStatus = "AWAY"
                ActivityType = "STREAMING"
                Activity = "RatBridge live"
                StreamUrl = "https://twitch.tv/datrat"
                UpdateInterval = 60
                """);
        Files.writeString(configDir.resolve("authentication.toml"), """
                authenticationEnabled = true
                authenticationCodeTtlMinutes = 15
                authenticationUnauthenticatedLoginMessageEnabled = false
                authenticationUnauthenticatedLoginMessage = "{player} needs auth"
                authenticationKickMessage = "Use code {code}\\nPlayer {player}"
                authenticationSuccessMessage = "Linked {player}"
                authenticationInvalidCodeMessage = "Bad code"
                authenticationAlreadyLinkedMessage = "Already linked"
                authenticationLogoutCommand = "r!logout"
                authenticationLogoutSuccessMessage = "Logged out"
                authenticationLogoutNotLinkedMessage = "Not linked"
                """);
        Files.writeString(configDir.resolve("messages.toml"), """
                commandsEnabled = true
                onlineCommand = "!online"
                onlineCommandDeleteAfterSeconds = 3
                onlinePlayersMessage = "{playercount}: {players}"
                onlineNoPlayersMessage = "nobody"
                minecraftToDiscordFormat = "[MC] {message} # not comment"
                discordReplyToMinecraftFormat = "[Discord] {author} -> {replyAuthor}: {message}"
                syncMinecraftToDiscordChat = false
                syncDiscordToMinecraftChat = true
                playerJoinMessage = "{player} entrou no jogo"
                syncPlayerDeath = false
                playerDeathMessage = "{message}"
                playerAdvancementMessage = "{player}: {advancement} - {description}"
                """);

        BridgeConfig loaded = BridgeConfigFile.loadSplit(configDir);

        assertEquals("selfbot", loaded.mode());
        assertEquals("42", loaded.channelId());
        assertEquals(500, loaded.selfbotPollIntervalMillis());
        assertEquals(true, loaded.webhookDelivery());
        assertEquals("RatBridge Chat", loaded.webhookName());
        assertEquals(true, loaded.topicUpdaterEnabled());
        assertEquals("99", loaded.topicUpdaterChannelId());
        assertEquals("%playercount% online", loaded.topicUpdaterMessage());
        assertEquals("offline", loaded.topicUpdaterShutdownMessage());
        assertEquals(15, loaded.topicUpdaterIntervalMinutes());
        assertEquals(true, loaded.channelNameUpdatersEnabled());
        assertEquals(2, loaded.channelNameUpdaters().size());
        assertEquals("100", loaded.channelNameUpdaters().get(0).channelId());
        assertEquals("%playercount% players", loaded.channelNameUpdaters().get(0).message());
        assertEquals("TPS %tps% # not comment", loaded.channelNameUpdaters().get(1).message());
        assertEquals(20, loaded.channelNameUpdaters().get(1).updateIntervalMinutes());
        assertEquals(true, loaded.botPresenceEnabled());
        assertEquals(2, loaded.botPresenceUpdates().size());
        assertEquals("DND", loaded.botPresenceUpdates().get(0).onlineStatus());
        assertEquals("WATCHING", loaded.botPresenceUpdates().get(0).activityType());
        assertEquals("%playercount% online # not comment", loaded.botPresenceUpdates().get(0).activity());
        assertEquals(30, loaded.botPresenceUpdates().get(0).updateIntervalSeconds());
        assertEquals("AWAY", loaded.botPresenceUpdates().get(1).onlineStatus());
        assertEquals("STREAMING", loaded.botPresenceUpdates().get(1).activityType());
        assertEquals("https://twitch.tv/datrat", loaded.botPresenceUpdates().get(1).streamUrl());
        assertEquals(true, loaded.authentication().enabled());
        assertEquals(15, loaded.authentication().codeTtlMinutes());
        assertEquals(false, loaded.authentication().unauthenticatedLoginMessageEnabled());
        assertEquals("{player} needs auth", loaded.authentication().unauthenticatedLoginMessage());
        assertEquals("Use code {code}\nPlayer {player}", loaded.authentication().kickMessage());
        assertEquals("Logged out", loaded.authentication().logoutSuccessMessage());
        assertEquals(true, loaded.discordCommands().enabled());
        assertEquals("!online", loaded.discordCommands().onlineCommand());
        assertEquals(3, loaded.discordCommands().onlineDeleteAfterSeconds());
        assertEquals("{playercount}: {players}", loaded.discordCommands().onlinePlayersMessage());
        assertEquals("nobody", loaded.discordCommands().onlineNoPlayersMessage());
        assertEquals("[MC] {message} # not comment", loaded.minecraftToDiscordFormat());
        assertEquals("[Discord] {author} -> {replyAuthor}: {message}", loaded.discordReplyToMinecraftFormat());
        assertEquals(false, loaded.syncMinecraftToDiscordChat());
        assertEquals(true, loaded.syncDiscordToMinecraftChat());
        assertEquals("{player} entrou no jogo", loaded.playerJoinMessage());
        assertEquals(false, loaded.syncPlayerDeath());
        assertEquals("{player}: {advancement} - {description}", loaded.playerAdvancementMessage());
    }

    @Test
    void migratesUpdaterValuesFromOldSplitConfigToml() throws Exception {
        Path configDir = tempDir.resolve("ratbridge");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.toml"), """
                mode = "bot"
                token = "abc"
                serverId = "24"
                channelId = "42"
                topicUpdaterEnabled = true
                topicUpdaterChannelId = "99"
                topicUpdaterMessage = "%playercount% online"
                topicUpdaterShutdownMessage = "offline"
                topicUpdaterIntervalMinutes = 20
                channelNameUpdaterCount = 1
                channelNameUpdater1ChannelId = "100"
                channelNameUpdater1Message = "%playercount% players"
                channelNameUpdater1ShutdownMessage = "offline"
                channelNameUpdater1UpdateInterval = 6
                """);
        Files.writeString(configDir.resolve("messages.toml"), "");

        BridgeConfig loaded = BridgeConfigFile.loadSplit(configDir);

        assertEquals(true, loaded.topicUpdaterEnabled());
        assertEquals("99", loaded.topicUpdaterChannelId());
        assertEquals("%playercount% online", loaded.topicUpdaterMessage());
        assertEquals(20, loaded.topicUpdaterIntervalMinutes());
        assertEquals(1, loaded.channelNameUpdaters().size());
        assertEquals("100", loaded.channelNameUpdaters().get(0).channelId());
        assertEquals("%playercount% players", loaded.channelNameUpdaters().get(0).message());
        assertTrue(Files.readString(configDir.resolve("topic-updater.toml")).contains("topicUpdaterEnabled = true"));
        assertTrue(Files.readString(configDir.resolve("topic-updater.toml")).contains("topicUpdaterChannelId = \"99\""));
        assertTrue(Files.readString(configDir.resolve("channel-updaters.toml")).contains("[[ChannelUpdater]]"));
        assertTrue(Files.readString(configDir.resolve("channel-updaters.toml")).contains("ChannelId = \"100\""));
    }

    @Test
    void migratesLegacyConfigIntoSplitFiles() throws Exception {
        Path legacy = tempDir.resolve("ratbridge.toml");
        Files.writeString(legacy, """
                mode = "selfbot"
                token = "abc"
                channelId = "42"
                enableSelfbot = true
                webhookDelivery = true
                webhookName = "Legacy Webhook"
                topicUpdaterEnabled = true
                topicUpdaterChannelId = "99"
                topicUpdaterMessage = "%playercount% online"
                topicUpdaterIntervalMinutes = 20
                channelNameUpdaterCount = 1
                channelNameUpdater1ChannelId = "100"
                channelNameUpdater1Message = "%playercount% players"
                channelNameUpdater1ShutdownMessage = "offline"
                channelNameUpdater1UpdateInterval = 6
                syncMinecraftToDiscordChat = false
                syncDiscordToMinecraftChat = true
                syncPlayerJoin = false
                syncPlayerDeath = false
                syncPlayerAdvancement = false
                playerJoinMessage = "{player} chegou"
                playerDeathMessage = "{message}"
                playerAdvancementMessage = "{player} desbloqueou {advancement}"
                """);

        BridgeConfig loaded = BridgeConfigFile.loadSplit(tempDir.resolve("ratbridge"));

        assertEquals("selfbot", loaded.mode());
        assertEquals("42", loaded.channelId());
        assertEquals(false, loaded.syncPlayerJoin());
        assertEquals(true, loaded.webhookDelivery());
        assertEquals("Legacy Webhook", loaded.webhookName());
        assertEquals(true, loaded.topicUpdaterEnabled());
        assertEquals("99", loaded.topicUpdaterChannelId());
        assertEquals("%playercount% online", loaded.topicUpdaterMessage());
        assertEquals(20, loaded.topicUpdaterIntervalMinutes());
        assertEquals(1, loaded.channelNameUpdaters().size());
        assertEquals("100", loaded.channelNameUpdaters().get(0).channelId());
        assertEquals("%playercount% players", loaded.channelNameUpdaters().get(0).message());
        assertEquals(false, loaded.syncMinecraftToDiscordChat());
        assertEquals(true, loaded.syncDiscordToMinecraftChat());
        assertEquals(false, loaded.syncPlayerDeath());
        assertEquals(false, loaded.syncPlayerAdvancement());
        assertEquals("{player} chegou", loaded.playerJoinMessage());
        assertEquals("{player} desbloqueou {advancement}", loaded.playerAdvancementMessage());
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("config.toml")).contains("mode = \"selfbot\""));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("config.toml")).contains("webhookDelivery = true"));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("topic-updater.toml")).contains("topicUpdaterEnabled = true"));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("topic-updater.toml")).contains("topicUpdaterChannelId = \"99\""));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("channel-updaters.toml")).contains("[[ChannelUpdater]]"));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("channel-updaters.toml")).contains("ChannelId = \"100\""));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("messages.toml")).contains("syncMinecraftToDiscordChat = false"));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("messages.toml")).contains("syncPlayerJoin = false"));
        assertTrue(Files.readString(tempDir.resolve("ratbridge").resolve("messages.toml")).contains("syncPlayerAdvancement = false"));
    }
}
