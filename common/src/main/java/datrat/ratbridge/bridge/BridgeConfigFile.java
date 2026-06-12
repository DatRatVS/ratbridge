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
        Path legacyPath = directory.getParent() == null ? null : directory.getParent().resolve("ratbridge.toml");
        Map<String, String> legacyValues = legacyPath != null && Files.exists(legacyPath) ? readValues(legacyPath) : Map.of();

        if (Files.notExists(configPath)) {
            writeDefaultConfig(configPath, legacyValues);
        }
        if (Files.notExists(messagesPath)) {
            writeDefaultMessages(messagesPath, legacyValues);
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(readValues(configPath));
        values.putAll(readValues(messagesPath));
        return fromValues(values);
    }

    public static BridgeConfig loadLegacy(Path path) throws IOException {
        if (Files.notExists(path)) {
            writeDefaultLegacy(path);
        }

        return fromValues(readValues(path));
    }

    public static void writeDefaultConfig(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultConfigToml(seedValues));
    }

    public static void writeDefaultMessages(Path path, Map<String, String> seedValues) throws IOException {
        write(path, defaultMessagesToml(seedValues));
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

    public static String defaultLegacyToml() {
        return defaultConfigToml() + "\n" + defaultMessagesToml();
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
                channelNameUpdaters(values),
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
                string(values, "minecraftToDiscordFormat", "[MC] <{player}> {message}"),
                string(values, "discordToMinecraftFormat", "[Discord] <{author}> {message}"),
                string(values, "eventFormat", "[MC] {message}"),
                string(values, "playerJoinMessage", "{player} joined the game"),
                string(values, "playerLeaveMessage", "{player} left the game"),
                string(values, "playerDeathMessage", "{message}"),
                string(values, "playerAdvancementMessage", "{player} has made the advancement [{advancement}]"),
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
                + "# \"selfbot\" uses a normal user account token for DM/Group DM channel polling.\n"
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
                + "# Discord server/guild ID. Required only in bot mode.\n"
                + "# Enable Discord developer mode, right-click the server, then Copy Server ID.\n"
                + "serverId = " + quote(string(values, "serverId", "")) + "\n"
                + "\n"
                + "# Discord channel ID to read/write messages.\n"
                + "# Bot mode: a text channel in the configured server.\n"
                + "# Selfbot mode: a DM or Group DM channel ID.\n"
                + "# Enable Discord developer mode, right-click the channel, then Copy Channel ID.\n"
                + "channelId = " + quote(string(values, "channelId", "")) + "\n"
                + "\n"
                + "# Extra selfbot safety gate. Selfbot mode will not start unless this is true.\n"
                + "# Keep false unless mode = \"selfbot\" and you accept the account ban risk.\n"
                + "enableSelfbot = " + boolString(values, "enableSelfbot", false) + "\n"
                + "\n"
                + "# Selfbot polling interval in milliseconds for DM/Group DM reads.\n"
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
                + "\n"
                + "# Discord channel topic updater. Bot mode only; selfbot mode cannot edit guild channel topics.\n"
                + "# The bot needs permission to manage channels in the target Discord channel.\n"
                + "topicUpdaterEnabled = " + boolString(values, "topicUpdaterEnabled", false) + "\n"
                + "\n"
                + "# Channel ID whose topic will be updated. Leave empty to use channelId above.\n"
                + "topicUpdaterChannelId = " + quote(string(values, "topicUpdaterChannelId", "")) + "\n"
                + "\n"
                + "# Topic text while the server is online.\n"
                + "# Placeholders: %playercount%, %playermax%, %totalplayers%, %uptimemins%, %uptimehours%, %motd%, %serverversion%, %tps%, %date%, %time%, %datetime%, %timestamp%\n"
                + "# Memory placeholders in MB: %freememory%, %usedmemory%, %totalmemory%, %maxmemory%\n"
                + "# Memory placeholders in GB: %freememorygb%, %usedmemorygb%, %totalmemorygb%, %maxmemorygb%\n"
                + "topicUpdaterMessage = " + quote(string(values, "topicUpdaterMessage", "Players: %playercount%/%playermax% | TPS: %tps% | Uptime: %uptimemins%m")) + "\n"
                + "\n"
                + "# Topic text applied during server shutdown. Uses the last server snapshot placeholders.\n"
                + "topicUpdaterShutdownMessage = " + quote(string(values, "topicUpdaterShutdownMessage", "Server is offline")) + "\n"
                + "\n"
                + "# Minutes between topic updates. Minimum: 5; 6+ is recommended to avoid Discord rate limits.\n"
                + "topicUpdaterIntervalMinutes = " + integer(values, "topicUpdaterIntervalMinutes", 6) + "\n"
                + "\n"
                + "# Discord channel name updaters. Bot mode only; the bot needs Manage Channels permission.\n"
                + "# Set channelNameUpdaterCount to how many numbered entries you want to use.\n"
                + "# Minimum update interval is 5 minutes; 6+ is recommended because Discord heavily rate-limits channel renames.\n"
                + "# Example:\n"
                + "# channelNameUpdaterCount = 2\n"
                + "# channelNameUpdater1ChannelId = \"000000000000000000\"\n"
                + "# channelNameUpdater1Message = \"%playercount% players online\"\n"
                + "# channelNameUpdater1ShutdownMessage = \"Server is offline\"\n"
                + "# channelNameUpdater1UpdateInterval = 6\n"
                + "# channelNameUpdater2ChannelId = \"000000000000000000\"\n"
                + "# channelNameUpdater2Message = \"TPS %tps%\"\n"
                + "# channelNameUpdater2ShutdownMessage = \"Server is offline\"\n"
                + "# channelNameUpdater2UpdateInterval = 6\n"
                + "channelNameUpdaterCount = " + integer(values, "channelNameUpdaterCount", 0) + "\n"
                + channelNameUpdaterEntriesToml(values);
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
                + "# Message formats.\n"
                + "# minecraftToDiscordFormat is used for Minecraft player chat sent to Discord.\n"
                + "# Available placeholders: {player}, {message}\n"
                + "minecraftToDiscordFormat = " + quote(string(values, "minecraftToDiscordFormat", "[MC] <{player}> {message}")) + "\n"
                + "\n"
                + "# discordToMinecraftFormat is used for Discord messages shown in Minecraft.\n"
                + "# Available placeholders: {author}, {message}\n"
                + "discordToMinecraftFormat = " + quote(string(values, "discordToMinecraftFormat", "[Discord] <{author}> {message}")) + "\n"
                + "\n"
                + "# eventFormat wraps server lifecycle and player join/leave messages before sending to Discord.\n"
                + "# Available placeholders: {message}\n"
                + "eventFormat = " + quote(string(values, "eventFormat", "[MC] {message}")) + "\n"
                + "\n"
                + "# Event message text before eventFormat is applied.\n"
                + "# playerJoinMessage and playerLeaveMessage support: {player}\n"
                + "playerJoinMessage = " + quote(string(values, "playerJoinMessage", "{player} joined the game")) + "\n"
                + "playerLeaveMessage = " + quote(string(values, "playerLeaveMessage", "{player} left the game")) + "\n"
                + "\n"
                + "# Death event text. Available placeholders: {player}, {message}\n"
                + "# {message} is Minecraft's localized vanilla death message, for example: Steve fell from a high place.\n"
                + "playerDeathMessage = " + quote(string(values, "playerDeathMessage", "{message}")) + "\n"
                + "\n"
                + "# Advancement event text. Available placeholders: {player}, {advancement}, {description}\n"
                + "playerAdvancementMessage = " + quote(string(values, "playerAdvancementMessage", "{player} has made the advancement [{advancement}]")) + "\n"
                + "\n"
                + "# Server lifecycle messages. No placeholders yet.\n"
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
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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

    private static String channelNameUpdaterEntriesToml(Map<String, String> values) {
        int count = Math.max(0, integer(values, "channelNameUpdaterCount", 0));
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            String prefix = "channelNameUpdater" + index;
            builder.append(prefix).append("ChannelId = ").append(quote(string(values, prefix + "ChannelId", ""))).append('\n');
            builder.append(prefix).append("Message = ").append(quote(string(values, prefix + "Message", "%playercount% players online"))).append('\n');
            builder.append(prefix).append("ShutdownMessage = ").append(quote(string(values, prefix + "ShutdownMessage", "Server is offline"))).append('\n');
            builder.append(prefix).append("UpdateInterval = ").append(integer(values, prefix + "UpdateInterval", 6)).append('\n');
        }
        return builder.toString();
    }
}
