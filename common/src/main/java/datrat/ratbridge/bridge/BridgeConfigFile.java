package datrat.ratbridge.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BridgeConfigFile {
    private BridgeConfigFile() {
    }

    public static BridgeConfig loadSplit(Path directory) throws IOException {
        Path configPath = directory.resolve("config.toml");
        Path messagesPath = directory.resolve("messages.toml");
        Path topicUpdaterPath = directory.resolve("topic-updater.toml");
        Path channelUpdatersPath = directory.resolve("channel-updaters.toml");
        Path botPresencePath = directory.resolve("bot-presence.toml");
        Path authenticationPath = directory.resolve("authentication.toml");
        Path legacyPath = directory.getParent() == null ? null : directory.getParent().resolve("ratbridge.toml");
        Map<String, String> legacyValues = legacyPath != null && Files.exists(legacyPath) ? readValuesWithTables(legacyPath) : Map.of();

        if (Files.notExists(configPath)) {
            writeDefaultConfig(configPath, legacyValues);
        }
        if (Files.notExists(messagesPath)) {
            writeDefaultMessages(messagesPath, legacyValues);
        }

        Map<String, String> configValues = readValues(configPath);
        Map<String, String> updaterSeedValues = new LinkedHashMap<>();
        updaterSeedValues.putAll(legacyValues);
        updaterSeedValues.putAll(configValues);

        if (Files.notExists(topicUpdaterPath)) {
            writeDefaultTopicUpdater(topicUpdaterPath, updaterSeedValues);
        }
        if (Files.notExists(channelUpdatersPath)) {
            writeDefaultChannelUpdaters(channelUpdatersPath, updaterSeedValues);
        }
        if (Files.notExists(botPresencePath)) {
            writeDefaultBotPresence(botPresencePath, legacyValues);
        }
        if (Files.notExists(authenticationPath)) {
            writeDefaultAuthentication(authenticationPath, legacyValues);
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(configValues);
        values.putAll(readValues(messagesPath));
        values.putAll(readValues(topicUpdaterPath));
        values.putAll(readValuesWithTables(channelUpdatersPath));
        values.putAll(readValuesWithTables(botPresencePath));
        values.putAll(readValues(authenticationPath));
        return fromValues(values);
    }

    public static BridgeConfig loadLegacy(Path path) throws IOException {
        if (Files.notExists(path)) {
            writeDefaultLegacy(path);
        }

        return fromValues(readValuesWithTables(path));
    }

    public static void writeDefaultConfig(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultConfigToml(seedValues));
    }

    public static void writeDefaultMessages(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultMessagesToml(seedValues));
    }

    public static void writeDefaultTopicUpdater(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultTopicUpdaterToml(seedValues));
    }

    public static void writeDefaultChannelUpdaters(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultChannelUpdatersToml(seedValues));
    }

    public static void writeDefaultBotPresence(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultBotPresenceToml(seedValues));
    }

    public static void writeDefaultAuthentication(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultAuthenticationToml(seedValues));
    }

    public static void writeDefaultLegacy(Path path) throws IOException {
        write(path, defaultLegacyToml());
    }

    public static String defaultConfigToml() {
        return defaultConfigToml(Map.of());
    }

    public static String defaultMessagesToml() {
        return defaultMessagesToml(Map.of());
    }

    public static String defaultTopicUpdaterToml() {
        return defaultTopicUpdaterToml(Map.of());
    }

    public static String defaultChannelUpdatersToml() {
        return defaultChannelUpdatersToml(Map.of());
    }

    public static String defaultBotPresenceToml() {
        return defaultBotPresenceToml(Map.of());
    }

    public static String defaultAuthenticationToml() {
        return defaultAuthenticationToml(Map.of());
    }

    public static String defaultLegacyToml() {
        return defaultConfigToml() + "\n" + defaultTopicUpdaterToml() + "\n" + defaultChannelUpdatersToml() + "\n" + defaultBotPresenceToml() + "\n" + defaultAuthenticationToml() + "\n" + defaultMessagesToml();
    }

    private static BridgeConfig fromValues(Map<String, String> values) {
        return new BridgeConfig(
                bool(values, "enabled", true),
                string(values, "client", "discord"),
                string(values, "mode", "bot"),
                string(values, "token", ""),
                string(values, "tokenEnv", "RATBRIDGE_DISCORD_TOKEN"),
                string(values, "serverId", ""),
                string(values, "channelId", ""),
                bool(values, "enableSelfbot", false),
                bool(values, "webhookDelivery", false),
                string(values, "webhookName", "RatBridge"),
                bool(values, "topicUpdaterEnabled", false),
                string(values, "topicUpdaterChannelId", ""),
                string(values, "topicUpdaterMessage", "Players: %playercount%/%playermax% | TPS: %tps% | Uptime: %uptimemins%m"),
                string(values, "topicUpdaterShutdownMessage", "Server is offline"),
                integer(values, "topicUpdaterIntervalMinutes", 6),
                bool(values, "channelNameUpdatersEnabled", integer(values, "channelNameUpdaterCount", 0) > 0),
                channelNameUpdaters(values),
                bool(values, "botPresenceEnabled", integer(values, "botPresenceCount", 0) > 0),
                botPresenceUpdates(values),
                authenticationConfig(values),
                discordCommandConfig(values),
                bool(values, "syncChat", true),
                bool(values, "syncMinecraftToDiscordChat", true),
                bool(values, "syncDiscordToMinecraftChat", true),
                bool(values, "syncPlayerJoin", true),
                bool(values, "syncPlayerLeave", true),
                bool(values, "syncPlayerDeath", true),
                bool(values, "syncPlayerAdvancement", true),
                bool(values, "syncServerStart", true),
                bool(values, "syncServerStop", true),
                integer(values, "selfbotPollIntervalMillis", 750),
                string(values, "minecraftToDiscordFormat", "[MC] <%player%> %message%"),
                string(values, "discordToMinecraftFormat", "[Discord] <%author%> %message%"),
                string(values, "discordReplyToMinecraftFormat", "[Discord] <%author%> replied to <%replyauthor%>: %message%"),
                string(values, "eventFormat", "[MC] %message%"),
                string(values, "playerJoinMessage", "%player% joined the game"),
                string(values, "playerLeaveMessage", "%player% left the game"),
                string(values, "playerDeathMessage", "%message%"),
                string(values, "playerAdvancementMessage", "%player% has made the advancement [%advancement%]"),
                string(values, "serverStartMessage", "Server started"),
                string(values, "serverStopMessage", "Server stopping")
        );
    }

    private static Map<String, String> readValues(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String stripped = stripComment(line).trim();
            if (stripped.isEmpty() || stripped.startsWith("[") || !stripped.contains("=")) {
                continue;
            }
            String[] parts = stripped.split("=", 2);
            values.put(parts[0].trim(), unquote(parts[1].trim()));
        }
        return values;
    }

    private static Map<String, String> readValuesWithTables(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        List<Map<String, String>> channelUpdaters = new ArrayList<>();
        List<Map<String, String>> botPresences = new ArrayList<>();
        Map<String, String> currentTable = null;
        String currentTableType = "";

        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String stripped = stripComment(line).trim();
            if (stripped.isEmpty()) {
                continue;
            }
            if ("[[ChannelUpdater]]".equals(stripped)) {
                currentTable = new LinkedHashMap<>();
                currentTableType = "ChannelUpdater";
                channelUpdaters.add(currentTable);
                continue;
            }
            if ("[[Presence]]".equals(stripped)) {
                currentTable = new LinkedHashMap<>();
                currentTableType = "Presence";
                botPresences.add(currentTable);
                continue;
            }
            if (stripped.startsWith("[")) {
                currentTable = null;
                currentTableType = "";
                continue;
            }
            if (!stripped.contains("=")) {
                continue;
            }

            String[] parts = stripped.split("=", 2);
            String key = parts[0].trim();
            String value = unquote(parts[1].trim());
            if (currentTable == null || currentTableType.isEmpty()) {
                values.put(key, value);
            } else {
                currentTable.put(key, value);
            }
        }

        if (!channelUpdaters.isEmpty()) {
            values.put("channelNameUpdaterCount", Integer.toString(channelUpdaters.size()));
            for (int index = 0; index < channelUpdaters.size(); index++) {
                Map<String, String> updater = channelUpdaters.get(index);
                String prefix = "channelNameUpdater" + (index + 1);
                values.put(prefix + "ChannelId", firstValue(updater, "ChannelId", "channelId", "channelID"));
                values.put(prefix + "Message", firstValue(updater, "Message", "message"));
                values.put(prefix + "ShutdownMessage", firstValue(updater, "ShutdownMessage", "shutdownMessage"));
                values.put(prefix + "UpdateInterval", firstValue(updater, "UpdateInterval", "updateInterval"));
            }
        }

        if (!botPresences.isEmpty()) {
            values.put("botPresenceCount", Integer.toString(botPresences.size()));
            for (int index = 0; index < botPresences.size(); index++) {
                Map<String, String> presence = botPresences.get(index);
                String prefix = "botPresence" + (index + 1);
                values.put(prefix + "OnlineStatus", firstValue(presence, "OnlineStatus", "onlineStatus", "Status", "status"));
                values.put(prefix + "ActivityType", firstValue(presence, "ActivityType", "activityType", "Type", "type"));
                values.put(prefix + "Activity", firstValue(presence, "Activity", "activity", "Text", "text"));
                values.put(prefix + "StreamUrl", firstValue(presence, "StreamUrl", "streamUrl", "StreamURL", "streamURL"));
                values.put(prefix + "UpdateInterval", firstValue(presence, "UpdateInterval", "updateInterval"));
            }
        }

        return values;
    }

    private static void write(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String defaultConfigToml(Map<String, String> values) {
        return """
                # Main RatBridge settings.
                # This file controls which external service/client is used and how RatBridge logs in.

                # Master switch. Set to false to keep the mod installed but stop all bridge activity.
                """
                + "enabled = " + boolString(values, "enabled", true) + "\n"
                + "\n"
                + "# External client to sync with. Currently supported: \"discord\".\n"
                + "client = " + quote(string(values, "client", "discord")) + "\n"
                + "\n"
                + "# Discord connection mode.\n"
                + "# \"bot\" uses a normal Discord bot token and needs serverId + channelId.\n"
                + "# \"selfbot\" uses a normal user account token for channel polling.\n"
                + "# Discord forbids selfbots; using selfbot mode can get that Discord account banned.\n"
                + "mode = " + quote(string(values, "mode", "bot")) + "\n"
                + "\n"
                + "# Discord token. You can paste the token here, but using tokenEnv is safer.\n"
                + "# Bot mode: use your Discord bot token.\n"
                + "# Selfbot mode: use the user account token only if you understand the ban risk.\n"
                + "# If token is empty, RatBridge will read the environment variable named by tokenEnv.\n"
                + "token = " + quote(string(values, "token", "")) + "\n"
                + "\n"
                + "# Name of the environment variable that stores the Discord token.\n"
                + "# Example on Linux: export RATBRIDGE_DISCORD_TOKEN=\"your-token-here\"\n"
                + "# Leave token empty above to use this.\n"
                + "tokenEnv = " + quote(string(values, "tokenEnv", "RATBRIDGE_DISCORD_TOKEN")) + "\n"
                + "\n"
                + "# Discord server/guild ID. Required in bot mode.\n"
                + "# Selfbot mode: required only when channelId is a server/guild channel; ignored for DM/Group DM channels.\n"
                + "# Enable Discord developer mode, right-click the server, then Copy Server ID.\n"
                + "serverId = " + quote(string(values, "serverId", "")) + "\n"
                + "\n"
                + "# Discord channel ID to read/write messages.\n"
                + "# Bot mode: a text channel in the configured server.\n"
                + "# Selfbot mode: a DM, Group DM, or server/guild text channel ID.\n"
                + "# Enable Discord developer mode, right-click the channel, then Copy Channel ID.\n"
                + "channelId = " + quote(string(values, "channelId", "")) + "\n"
                + "\n"
                + "# Extra selfbot safety gate. Selfbot mode will not start unless this is true.\n"
                + "# Keep false unless mode = \"selfbot\" and you accept the account ban risk.\n"
                + "enableSelfbot = " + boolString(values, "enableSelfbot", false) + "\n"
                + "\n"
                + "# Selfbot polling interval in milliseconds for channel reads.\n"
                + "# Lower values reduce delay but can hit Discord rate limits faster. Minimum: 500.\n"
                + "selfbotPollIntervalMillis = " + integer(values, "selfbotPollIntervalMillis", 750) + "\n"
                + "\n"
                + "# Send Minecraft player chat through a Discord webhook instead of normal bot messages.\n"
                + "# This only affects Minecraft player chat. Join/leave/start/stop events still use normal bot messages.\n"
                + "# Webhook mode mirrors Minecraft chat cleanly: username = Minecraft name, avatar = Minotar helm skin, content = message.\n"
                + "# Requires mode = \"bot\" and the bot needs permission to manage webhooks in the Discord channel.\n"
                + "# This is strictly blocked for selfbot mode.\n"
                + "webhookDelivery = " + boolString(values, "webhookDelivery", false) + "\n"
                + "\n"
                + "# Name of the webhook RatBridge creates/reuses in the configured Discord channel.\n"
                + "webhookName = " + quote(string(values, "webhookName", "RatBridge")) + "\n"
                + "\n";
    }

    private static String defaultTopicUpdaterToml(Map<String, String> values) {
        return """
                # RatBridge Discord channel topic updater.
                # This file controls one optional updater that edits a Discord channel topic with server status.
                
                """
                + "# Discord channel topic updater. Bot mode only; selfbot mode cannot edit guild channel topics.\n"
                + "# The bot needs permission to manage channels in the target Discord channel.\n"
                + "topicUpdaterEnabled = " + boolString(values, "topicUpdaterEnabled", false) + "\n"
                + "\n"
                + "# Channel ID whose topic will be updated. Leave empty to use channelId above.\n"
                + "topicUpdaterChannelId = " + quote(string(values, "topicUpdaterChannelId", "")) + "\n"
                + "\n"
                + "# Topic text while the server is online.\n"
                + "# Placeholders: %playercount%, %playermax%, %totalplayers%, %uptimemins%, %uptimehours%, %motd%, %serverversion%, %ratbridgeversion%, %tps%, %date%, %time%, %datetime%, %timestamp%\n"
                + "# Memory placeholders in MB: %freememory%, %usedmemory%, %totalmemory%, %maxmemory%\n"
                + "# Memory placeholders in GB: %freememorygb%, %usedmemorygb%, %totalmemorygb%, %maxmemorygb%\n"
                + "topicUpdaterMessage = " + quote(string(values, "topicUpdaterMessage", "Players: %playercount%/%playermax% | TPS: %tps% | Uptime: %uptimemins%m")) + "\n"
                + "\n"
                + "# Topic text applied during server shutdown. Uses the last server snapshot placeholders.\n"
                + "topicUpdaterShutdownMessage = " + quote(string(values, "topicUpdaterShutdownMessage", "Server is offline")) + "\n"
                + "\n"
                + "# Minutes between topic updates. Minimum: 5; 6+ is recommended to avoid Discord rate limits.\n"
                + "topicUpdaterIntervalMinutes = " + integer(values, "topicUpdaterIntervalMinutes", 6) + "\n"
                + "\n";
    }

    private static String defaultChannelUpdatersToml(Map<String, String> values) {
        return """
                # RatBridge Discord channel name updaters.
                # This file controls optional updaters that rename Discord channels with server status.
                
                """
                + "# Discord channel name updaters. Bot mode only; the bot needs Manage Channels permission.\n"
                + "# Master switch. Set to true to enable every [[ChannelUpdater]] block below.\n"
                + "channelNameUpdatersEnabled = " + boolString(values, "channelNameUpdatersEnabled", false) + "\n"
                + "\n"
                + "# Add one [[ChannelUpdater]] block for each Discord channel name RatBridge should update.\n"
                + "# Placeholders: %playercount%, %playermax%, %totalplayers%, %uptimemins%, %uptimehours%, %motd%, %serverversion%, %ratbridgeversion%, %tps%, %date%, %time%, %datetime%, %timestamp%\n"
                + "# Memory placeholders in MB: %freememory%, %usedmemory%, %totalmemory%, %maxmemory%\n"
                + "# Memory placeholders in GB: %freememorygb%, %usedmemorygb%, %totalmemorygb%, %maxmemorygb%\n"
                + "# Minimum update interval is 5 minutes; 6+ is recommended because Discord heavily rate-limits channel renames.\n"
                + "# ChannelId: Discord channel ID to rename.\n"
                + "# Message: Channel name while the Minecraft server is online.\n"
                + "# ShutdownMessage: Channel name applied while the Minecraft server is stopping.\n"
                + "# UpdateInterval: Minutes between channel name updates.\n"
                + "# Example:\n"
                + "# [[ChannelUpdater]]\n"
                + "# ChannelId = \"000000000000000000\"\n"
                + "# Message = \"%playercount% players online\"\n"
                + "# ShutdownMessage = \"Server is offline\"\n"
                + "# UpdateInterval = 6\n"
                + "#\n"
                + "# [[ChannelUpdater]]\n"
                + "# ChannelId = \"000000000000000000\"\n"
                + "# Message = \"TPS %tps%\"\n"
                + "# ShutdownMessage = \"Server is offline\"\n"
                + "# UpdateInterval = 6\n"
                + channelNameUpdaterTablesToml(values);
    }

    private static String defaultBotPresenceToml(Map<String, String> values) {
        return """
                # RatBridge Discord bot presence updater.
                # This file controls optional bot online status and activity rotation. Bot mode only.
                
                """
                + "# Add one [[Presence]] block for each status/activity RatBridge should rotate through.\n"
                + "# Master switch. Set to true to enable every [[Presence]] block below.\n"
                + "botPresenceEnabled = " + boolString(values, "botPresenceEnabled", false) + "\n"
                + "\n"
                + "# If this file has no [[Presence]] blocks, bot presence updates are disabled.\n"
                + "# OnlineStatus: ONLINE, IDLE, AWAY, DND, DO_NOT_DISTURB, or INVISIBLE.\n"
                + "# ActivityType: PLAYING, LISTENING, WATCHING, STREAMING, COMPETING, or CUSTOM.\n"
                + "# Activity: text shown in the bot activity. Supports the same placeholders as topic/channel updaters.\n"
                + "# Placeholders: %playercount%, %playermax%, %totalplayers%, %uptimemins%, %uptimehours%, %motd%, %serverversion%, %ratbridgeversion%, %tps%, %date%, %time%, %datetime%, %timestamp%\n"
                + "# Memory placeholders in MB: %freememory%, %usedmemory%, %totalmemory%, %maxmemory%\n"
                + "# Memory placeholders in GB: %freememorygb%, %usedmemorygb%, %totalmemorygb%, %maxmemorygb%\n"
                + "# StreamUrl: required only when ActivityType = STREAMING.\n"
                + "# UpdateInterval: seconds before RatBridge moves to the next block. Minimum: 30.\n"
                + "# Example:\n"
                + "# [[Presence]]\n"
                + "# OnlineStatus = \"ONLINE\"\n"
                + "# ActivityType = \"PLAYING\"\n"
                + "# Activity = \"%playercount%/%playermax% players\"\n"
                + "# UpdateInterval = 60\n"
                + "#\n"
                + "# [[Presence]]\n"
                + "# OnlineStatus = \"DND\"\n"
                + "# ActivityType = \"WATCHING\"\n"
                + "# Activity = \"TPS %tps%\"\n"
                + "# UpdateInterval = 60\n"
                + botPresenceTablesToml(values);
    }

    private static String defaultAuthenticationToml(Map<String, String> values) {
        return """
                # RatBridge Discord authentication.
                # When enabled, players must link one Minecraft account to one Discord account before they can play.
                # Flow:
                # 1. Player joins Minecraft.
                # 2. RatBridge disconnects them with a six digit code.
                # 3. Player sends that code to the Discord bot DM.
                # 4. RatBridge stores the Minecraft <-> Discord link and the player can join again.
                """
                + "# Master switch for Discord-driven authentication.\n"
                + "authenticationEnabled = " + boolString(values, "authenticationEnabled", false) + "\n"
                + "\n"
                + "# Minutes before an unused join code expires. A new code is generated on the next join attempt.\n"
                + "authenticationCodeTtlMinutes = " + integer(values, "authenticationCodeTtlMinutes", 10) + "\n"
                + "\n"
                + "# Minecraft names that bypass linking and Discord access checks.\n"
                + "authenticationBypassNames = " + stringListToml(stringList(values, "authenticationBypassNames", List.of())) + "\n"
                + "\n"
                + "# If true, players on the vanilla Minecraft whitelist bypass linking and Discord access checks.\n"
                + "authenticationWhitelistedPlayersBypass = " + boolString(values, "authenticationWhitelistedPlayersBypass", true) + "\n"
                + "\n"
                + "# If true, players on the vanilla Minecraft banlist are still checked by RatBridge.\n"
                + "authenticationCheckBannedPlayers = " + boolString(values, "authenticationCheckBannedPlayers", false) + "\n"
                + "\n"
                + "# If true, only players on the vanilla Minecraft banlist are checked by RatBridge; everyone else bypasses.\n"
                + "authenticationOnlyCheckBannedPlayers = " + boolString(values, "authenticationOnlyCheckBannedPlayers", false) + "\n"
                + "\n"
                + "# Optional Discord server membership requirement for linked accounts.\n"
                + "# Accepted values: false, true, a server ID string, or a list of server ID strings.\n"
                + "# true requires membership in at least one Discord server where the bot is present.\n"
                + "# A list requires membership in every listed server.\n"
                + "authenticationRequiredDiscordServers = " + quote(string(values, "authenticationRequiredDiscordServers", "false")) + "\n"
                + "\n"
                + "# Invite text used by %invite% in denial messages.\n"
                + "authenticationDiscordInvite = " + quote(string(values, "authenticationDiscordInvite", "")) + "\n"
                + "\n"
                + "# Require at least one or all listed Discord role IDs for linked accounts.\n"
                + "# Bot mode only; the bot needs access to the server and member information.\n"
                + "authenticationRequireSubscriberRole = " + boolString(values, "authenticationRequireSubscriberRole", false) + "\n"
                + "authenticationSubscriberRoles = " + stringListToml(stringList(values, "authenticationSubscriberRoles", List.of())) + "\n"
                + "authenticationRequireAllSubscriberRoles = " + boolString(values, "authenticationRequireAllSubscriberRoles", false) + "\n"
                + "\n"
                + "# Kick message when the linked Discord account is missing the required role.\n"
                + "# Placeholders: %player%, %uuid%, %discord%, %invite%, %ratbridgeversion%\n"
                + "authenticationSubscriberRoleKickMessage = " + quote(string(values, "authenticationSubscriberRoleKickMessage", "You must have the required Discord role to join this server.")) + "\n"
                + "\n"
                + "# Kick message when the linked Discord account is not in the required Discord server.\n"
                + "# Placeholders: %player%, %uuid%, %discord%, %invite%, %ratbridgeversion%\n"
                + "authenticationNotInServerMessage = " + quote(string(values, "authenticationNotInServerMessage", "You are not currently in the required Discord server.")) + "\n"
                + "\n"
                + "# Kick message when none of the configured role IDs can be found on Discord.\n"
                + "# Placeholders: %player%, %uuid%, %discord%, %invite%, %ratbridgeversion%\n"
                + "authenticationMissingSubscriberRoleMessage = " + quote(string(values, "authenticationMissingSubscriberRoleMessage", "RatBridge could not find any configured subscriber role. Contact a server admin.")) + "\n"
                + "\n"
                + "# Kick message when Discord access verification fails for an unknown reason.\n"
                + "# Placeholders: %player%, %uuid%, %discord%, %invite%, %ratbridgeversion%\n"
                + "authenticationRoleCheckFailedMessage = " + quote(string(values, "authenticationRoleCheckFailedMessage", "RatBridge could not verify your Discord access. Contact a server admin.")) + "\n"
                + "\n"
                + "# Sends a Discord event when an unauthenticated player attempts to join.\n"
                + "# This replaces the misleading leave message caused by the server disconnecting the player during login.\n"
                + "authenticationUnauthenticatedLoginMessageEnabled = " + boolString(values, "authenticationUnauthenticatedLoginMessageEnabled", true) + "\n"
                + "\n"
                + "# Discord event text for unauthenticated join attempts.\n"
                + "# Placeholders: %player%, %uuid%, %ratbridgeversion%\n"
                + "authenticationUnauthenticatedLoginMessage = " + quote(string(values, "authenticationUnauthenticatedLoginMessage", "%player% tried to join but is not authenticated yet.")) + "\n"
                + "\n"
                + "# Message shown on the Minecraft disconnect screen while the account is not authenticated.\n"
                + "# Use \\n inside the string for line breaks.\n"
                + "# Placeholders: %player%, %uuid%, %ratbridgeversion%, %code%, %logoutcommand%\n"
                + "authenticationKickMessage = " + quote(string(values, "authenticationKickMessage", "This server requires Discord authentication.\nSend code %code% to the RatBridge bot DM to authenticate %player%.")) + "\n"
                + "\n"
                + "# DM response after a code is accepted.\n"
                + "# Placeholders: %player%, %discord%, %code%, %ratbridgeversion%\n"
                + "authenticationSuccessMessage = " + quote(string(values, "authenticationSuccessMessage", "Authenticated %player%. You can now join the server.")) + "\n"
                + "\n"
                + "# DM response when the user sends an invalid or expired six digit code.\n"
                + "# Placeholders: %discord%, %code%, %ratbridgeversion%\n"
                + "authenticationInvalidCodeMessage = " + quote(string(values, "authenticationInvalidCodeMessage", "Invalid or expired authentication code.")) + "\n"
                + "\n"
                + "# DM response when either side of the link is already used by another account.\n"
                + "# This prevents one Minecraft account from linking to two Discord accounts and vice versa.\n"
                + "# Placeholders: %player%, %discord%, %code%, %ratbridgeversion%\n"
                + "authenticationAlreadyLinkedMessage = " + quote(string(values, "authenticationAlreadyLinkedMessage", "That Minecraft or Discord account is already linked to another account.")) + "\n"
                + "\n"
                + "# DM command that removes the Discord user's current link.\n"
                + "authenticationLogoutCommand = " + quote(string(values, "authenticationLogoutCommand", "r!logout")) + "\n"
                + "\n"
                + "# DM response after the logout command removes a link.\n"
                + "# Placeholders: %player%, %uuid%, %discord%, %ratbridgeversion%\n"
                + "authenticationLogoutSuccessMessage = " + quote(string(values, "authenticationLogoutSuccessMessage", "Your Minecraft account link was removed. Join the server again to get a new code.")) + "\n"
                + "\n"
                + "# DM response when the logout command is used by a Discord account with no link.\n"
                + "# Placeholders: %discord%, %ratbridgeversion%\n"
                + "authenticationLogoutNotLinkedMessage = " + quote(string(values, "authenticationLogoutNotLinkedMessage", "Your Discord account is not linked to any Minecraft account.")) + "\n";
    }

    private static String defaultMessagesToml(Map<String, String> values) {
        return """
                # RatBridge message settings.
                # This file controls which events are synced and what text RatBridge sends.
                
                # Listener toggles.
                # Set a value to false to stop syncing that specific message/event type.
                # syncChat is a master/legacy chat toggle. Set false to disable both chat directions.
                # syncMinecraftToDiscordChat controls Minecraft player chat sent to Discord.
                # syncDiscordToMinecraftChat controls Discord messages shown in Minecraft.
                """
                + "syncChat = " + boolString(values, "syncChat", true) + "\n"
                + "syncMinecraftToDiscordChat = " + boolString(values, "syncMinecraftToDiscordChat", true) + "\n"
                + "syncDiscordToMinecraftChat = " + boolString(values, "syncDiscordToMinecraftChat", true) + "\n"
                + "syncPlayerJoin = " + boolString(values, "syncPlayerJoin", true) + "\n"
                + "syncPlayerLeave = " + boolString(values, "syncPlayerLeave", true) + "\n"
                + "syncPlayerDeath = " + boolString(values, "syncPlayerDeath", true) + "\n"
                + "syncPlayerAdvancement = " + boolString(values, "syncPlayerAdvancement", true) + "\n"
                + "syncServerStart = " + boolString(values, "syncServerStart", true) + "\n"
                + "syncServerStop = " + boolString(values, "syncServerStop", true) + "\n"
                + "\n"
                + "# Discord command settings.\n"
                + "# commandsEnabled is the master switch for Discord text commands handled by RatBridge.\n"
                + "commandsEnabled = " + boolString(values, "commandsEnabled", true) + "\n"
                + "\n"
                + "# Command users can send in the configured bridge channel to see online Minecraft players.\n"
                + "onlineCommand = " + quote(string(values, "onlineCommand", "r!online")) + "\n"
                + "\n"
                + "# Seconds before RatBridge deletes its online command response. Minimum: 1.\n"
                + "onlineCommandDeleteAfterSeconds = " + integer(values, "onlineCommandDeleteAfterSeconds", 10) + "\n"
                + "\n"
                + "# Online command response when at least one player is online.\n"
                + "# Available placeholders: %playercount%, %playerplural%, %players%, %ratbridgeversion%\n"
                + "onlinePlayersMessage = " + quote(string(values, "onlinePlayersMessage", "**%playercount% online player%playerplural%:** %players%")) + "\n"
                + "\n"
                + "# Online command response when no players are online.\n"
                + "# Available placeholders: %playercount%, %playerplural%, %players%, %ratbridgeversion%\n"
                + "onlineNoPlayersMessage = " + quote(string(values, "onlineNoPlayersMessage", "**No online players.**")) + "\n"
                + "\n"
                + "# Message formats.\n"
                + "# minecraftToDiscordFormat is used for Minecraft player chat sent to Discord.\n"
                + "# Available placeholders: %player%, %message%, %ratbridgeversion%\n"
                + "minecraftToDiscordFormat = " + quote(string(values, "minecraftToDiscordFormat", "[MC] <%player%> %message%")) + "\n"
                + "\n"
                + "# discordToMinecraftFormat is used for Discord messages shown in Minecraft.\n"
                + "# Available placeholders: %author%, %message%, %ratbridgeversion%\n"
                + "discordToMinecraftFormat = " + quote(string(values, "discordToMinecraftFormat", "[Discord] <%author%> %message%")) + "\n"
                + "\n"
                + "# discordReplyToMinecraftFormat is used for Discord reply messages shown in Minecraft.\n"
                + "# Available placeholders: %author%, %replyauthor%, %message%, %ratbridgeversion%\n"
                + "discordReplyToMinecraftFormat = " + quote(string(values, "discordReplyToMinecraftFormat", "[Discord] <%author%> replied to <%replyauthor%>: %message%")) + "\n"
                + "\n"
                + "# eventFormat wraps server lifecycle and player join/leave messages before sending to Discord.\n"
                + "# Available placeholders: %message%, %ratbridgeversion%\n"
                + "eventFormat = " + quote(string(values, "eventFormat", "[MC] %message%")) + "\n"
                + "\n"
                + "# Event message text before eventFormat is applied.\n"
                + "# playerJoinMessage and playerLeaveMessage support: %player%, %ratbridgeversion%\n"
                + "playerJoinMessage = " + quote(string(values, "playerJoinMessage", "%player% joined the game")) + "\n"
                + "playerLeaveMessage = " + quote(string(values, "playerLeaveMessage", "%player% left the game")) + "\n"
                + "\n"
                + "# Death event text. Available placeholders: %player%, %message%, %ratbridgeversion%\n"
                + "# %message% is Minecraft's localized vanilla death message, for example: Steve fell from a high place.\n"
                + "playerDeathMessage = " + quote(string(values, "playerDeathMessage", "%message%")) + "\n"
                + "\n"
                + "# Advancement event text. Available placeholders: %player%, %advancement%, %description%, %ratbridgeversion%\n"
                + "playerAdvancementMessage = " + quote(string(values, "playerAdvancementMessage", "%player% has made the advancement [%advancement%]")) + "\n"
                + "\n"
                + "# Server lifecycle messages can be wrapped by eventFormat, which supports %ratbridgeversion%.\n"
                + "serverStartMessage = " + quote(string(values, "serverStartMessage", "Server started")) + "\n"
                + "serverStopMessage = " + quote(string(values, "serverStopMessage", "Server stopping")) + "\n";
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (value == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String string(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private static String firstValue(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static String boolString(Map<String, String> values, String key, boolean fallback) {
        return Boolean.toString(bool(values, key, fallback));
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\"";
    }

    private static List<String> stringList(Map<String, String> values, String key, List<String> fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        return AuthenticationConfig.parseStringList(value);
    }

    private static String stringListToml(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream().map(BridgeConfigFile::quote).collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private static List<ChannelNameUpdaterConfig> channelNameUpdaters(Map<String, String> values) {
        int count = Math.max(0, integer(values, "channelNameUpdaterCount", 0));
        List<ChannelNameUpdaterConfig> updaters = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String prefix = "channelNameUpdater" + index;
            updaters.add(new ChannelNameUpdaterConfig(
                    string(values, prefix + "ChannelId", ""),
                    string(values, prefix + "Message", "%playercount% players online"),
                    string(values, prefix + "ShutdownMessage", "Server is offline"),
                    integer(values, prefix + "UpdateInterval", 6)
            ));
        }
        return List.copyOf(updaters);
    }

    private static List<BotPresenceConfig> botPresenceUpdates(Map<String, String> values) {
        int count = Math.max(0, integer(values, "botPresenceCount", 0));
        List<BotPresenceConfig> presences = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            String prefix = "botPresence" + index;
            presences.add(new BotPresenceConfig(
                    string(values, prefix + "OnlineStatus", "ONLINE"),
                    string(values, prefix + "ActivityType", "PLAYING"),
                    string(values, prefix + "Activity", ""),
                    string(values, prefix + "StreamUrl", ""),
                    integer(values, prefix + "UpdateInterval", 60)
            ));
        }
        return List.copyOf(presences);
    }

    private static AuthenticationConfig authenticationConfig(Map<String, String> values) {
        AuthenticationConfig fallback = AuthenticationConfig.disabled();
        return new AuthenticationConfig(
                bool(values, "authenticationEnabled", fallback.enabled()),
                integer(values, "authenticationCodeTtlMinutes", fallback.codeTtlMinutes()),
                stringList(values, "authenticationBypassNames", fallback.bypassNames()),
                bool(values, "authenticationWhitelistedPlayersBypass", fallback.whitelistedPlayersBypass()),
                bool(values, "authenticationCheckBannedPlayers", fallback.checkBannedPlayers()),
                bool(values, "authenticationOnlyCheckBannedPlayers", fallback.onlyCheckBannedPlayers()),
                string(values, "authenticationRequiredDiscordServers", fallback.requiredDiscordServers()),
                bool(values, "authenticationRequireSubscriberRole", fallback.requireSubscriberRole()),
                stringList(values, "authenticationSubscriberRoles", fallback.subscriberRoles()),
                bool(values, "authenticationRequireAllSubscriberRoles", fallback.requireAllSubscriberRoles()),
                string(values, "authenticationSubscriberRoleKickMessage", fallback.subscriberRoleKickMessage()),
                string(values, "authenticationNotInServerMessage", fallback.notInServerMessage()),
                string(values, "authenticationMissingSubscriberRoleMessage", fallback.missingSubscriberRoleMessage()),
                string(values, "authenticationRoleCheckFailedMessage", fallback.roleCheckFailedMessage()),
                string(values, "authenticationDiscordInvite", fallback.discordInvite()),
                bool(values, "authenticationUnauthenticatedLoginMessageEnabled", fallback.unauthenticatedLoginMessageEnabled()),
                string(values, "authenticationUnauthenticatedLoginMessage", fallback.unauthenticatedLoginMessage()),
                string(values, "authenticationKickMessage", fallback.kickMessage()),
                string(values, "authenticationSuccessMessage", fallback.successMessage()),
                string(values, "authenticationInvalidCodeMessage", fallback.invalidCodeMessage()),
                string(values, "authenticationAlreadyLinkedMessage", fallback.alreadyLinkedMessage()),
                string(values, "authenticationLogoutCommand", fallback.logoutCommand()),
                string(values, "authenticationLogoutSuccessMessage", fallback.logoutSuccessMessage()),
                string(values, "authenticationLogoutNotLinkedMessage", fallback.logoutNotLinkedMessage())
        );
    }

    private static DiscordCommandConfig discordCommandConfig(Map<String, String> values) {
        DiscordCommandConfig fallback = DiscordCommandConfig.defaults();
        return new DiscordCommandConfig(
                bool(values, "commandsEnabled", fallback.enabled()),
                string(values, "onlineCommand", fallback.onlineCommand()),
                integer(values, "onlineCommandDeleteAfterSeconds", fallback.onlineDeleteAfterSeconds()),
                string(values, "onlinePlayersMessage", fallback.onlinePlayersMessage()),
                string(values, "onlineNoPlayersMessage", fallback.onlineNoPlayersMessage())
        );
    }

    private static String channelNameUpdaterTablesToml(Map<String, String> values) {
        int count = Math.max(0, integer(values, "channelNameUpdaterCount", 0));
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            String prefix = "channelNameUpdater" + index;
            builder.append('\n');
            builder.append("[[ChannelUpdater]]\n");
            builder.append("ChannelId = ").append(quote(string(values, prefix + "ChannelId", ""))).append('\n');
            builder.append("Message = ").append(quote(string(values, prefix + "Message", "%playercount% players online"))).append('\n');
            builder.append("ShutdownMessage = ").append(quote(string(values, prefix + "ShutdownMessage", "Server is offline"))).append('\n');
            builder.append("UpdateInterval = ").append(integer(values, prefix + "UpdateInterval", 6)).append('\n');
        }
        return builder.toString();
    }

    private static String botPresenceTablesToml(Map<String, String> values) {
        int count = Math.max(0, integer(values, "botPresenceCount", 0));
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            String prefix = "botPresence" + index;
            builder.append('\n');
            builder.append("[[Presence]]\n");
            builder.append("OnlineStatus = ").append(quote(string(values, prefix + "OnlineStatus", "ONLINE"))).append('\n');
            builder.append("ActivityType = ").append(quote(string(values, prefix + "ActivityType", "PLAYING"))).append('\n');
            builder.append("Activity = ").append(quote(string(values, prefix + "Activity", ""))).append('\n');
            builder.append("StreamUrl = ").append(quote(string(values, prefix + "StreamUrl", ""))).append('\n');
            builder.append("UpdateInterval = ").append(integer(values, prefix + "UpdateInterval", 60)).append('\n');
        }
        return builder.toString();
    }
}
