package datrat.ratbridge.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BridgeConfigFile {
    private BridgeConfigFile() {
    }

    public static BridgeConfig load(Path path) throws IOException {
        if (Files.notExists(path)) {
            writeDefault(path);
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String stripped = stripComment(line).trim();
            if (stripped.isEmpty() || stripped.startsWith("[") || !stripped.contains("=")) {
                continue;
            }
            String[] parts = stripped.split("=", 2);
            values.put(parts[0].trim(), unquote(parts[1].trim()));
        }

        return new BridgeConfig(
                bool(values, "enabled", true),
                string(values, "client", "discord"),
                string(values, "mode", "bot"),
                string(values, "token", ""),
                string(values, "tokenEnv", "RATBRIDGE_DISCORD_TOKEN"),
                string(values, "serverId", ""),
                string(values, "channelId", ""),
                bool(values, "enableSelfbot", false),
                bool(values, "syncChat", true),
                bool(values, "syncPlayerJoin", true),
                bool(values, "syncPlayerLeave", true),
                bool(values, "syncServerStart", true),
                bool(values, "syncServerStop", true),
                integer(values, "selfbotPollIntervalMillis", 750),
                string(values, "minecraftToDiscordFormat", "[MC] <{player}> {message}"),
                string(values, "discordToMinecraftFormat", "[Discord] <{author}> {message}"),
                string(values, "eventFormat", "[MC] {message}")
        );
    }

    public static void writeDefault(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, defaultToml(), StandardCharsets.UTF_8);
    }

    public static String defaultToml() {
        return """
                enabled = true
                client = "discord"
                mode = "bot"

                token = ""
                tokenEnv = "RATBRIDGE_DISCORD_TOKEN"

                serverId = ""
                channelId = ""

                enableSelfbot = false
                selfbotPollIntervalMillis = 750

                syncChat = true
                syncPlayerJoin = true
                syncPlayerLeave = true
                syncServerStart = true
                syncServerStop = true

                minecraftToDiscordFormat = "[MC] <{player}> {message}"
                discordToMinecraftFormat = "[Discord] <{author}> {message}"
                eventFormat = "[MC] {message}"
                """;
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
}
