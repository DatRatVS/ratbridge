package datrat.ratbridge.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AuthenticationStore {
    private final Path path;
    private final Map<String, AuthenticatedAccount> byMinecraftUuid = new LinkedHashMap<>();
    private final Map<String, String> minecraftUuidByDiscordUserId = new LinkedHashMap<>();

    public AuthenticationStore(Path path) {
        this.path = path;
    }

    public synchronized void load() throws IOException {
        byMinecraftUuid.clear();
        minecraftUuidByDiscordUserId.clear();
        if (Files.notExists(path)) {
            write();
            return;
        }

        Map<String, String> current = null;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String stripped = stripComment(line).trim();
            if (stripped.isEmpty()) {
                continue;
            }
            if ("[[Account]]".equals(stripped)) {
                putIfValid(current);
                current = new LinkedHashMap<>();
                continue;
            }
            if (stripped.startsWith("[")) {
                putIfValid(current);
                current = null;
                continue;
            }
            if (current == null || !stripped.contains("=")) {
                continue;
            }
            String[] parts = stripped.split("=", 2);
            current.put(parts[0].trim(), unquote(parts[1].trim()));
        }
        putIfValid(current);
    }

    public synchronized Optional<AuthenticatedAccount> findByMinecraftUuid(String minecraftUuid) {
        return Optional.ofNullable(byMinecraftUuid.get(minecraftUuid));
    }

    public synchronized Optional<AuthenticatedAccount> findByDiscordUserId(String discordUserId) {
        String minecraftUuid = minecraftUuidByDiscordUserId.get(discordUserId);
        return minecraftUuid == null ? Optional.empty() : Optional.ofNullable(byMinecraftUuid.get(minecraftUuid));
    }

    public synchronized boolean isMinecraftLinkedToOtherDiscord(String minecraftUuid, String discordUserId) {
        AuthenticatedAccount account = byMinecraftUuid.get(minecraftUuid);
        return account != null && !account.discordUserId().equals(discordUserId);
    }

    public synchronized boolean isDiscordLinkedToOtherMinecraft(String discordUserId, String minecraftUuid) {
        String linkedMinecraftUuid = minecraftUuidByDiscordUserId.get(discordUserId);
        return linkedMinecraftUuid != null && !linkedMinecraftUuid.equals(minecraftUuid);
    }

    public synchronized void link(String minecraftUuid, String minecraftName, String discordUserId, String discordName) throws IOException {
        put(new AuthenticatedAccount(
                minecraftUuid,
                minecraftName,
                discordUserId,
                discordName,
                Instant.now().toString()
        ));
        write();
    }

    public synchronized boolean unlinkByDiscordUserId(String discordUserId) throws IOException {
        String minecraftUuid = minecraftUuidByDiscordUserId.remove(discordUserId);
        if (minecraftUuid == null) {
            return false;
        }
        byMinecraftUuid.remove(minecraftUuid);
        write();
        return true;
    }

    private void put(AuthenticatedAccount account) {
        byMinecraftUuid.put(account.minecraftUuid(), account);
        minecraftUuidByDiscordUserId.put(account.discordUserId(), account.minecraftUuid());
    }

    private void putIfValid(Map<String, String> values) {
        AuthenticatedAccount account = values == null ? null : accountFrom(values);
        if (account != null) {
            put(account);
        }
    }

    private void write() throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# RatBridge authentication state. This file is managed by the mod.\n");
        builder.append("# Delete an [[Account]] block to force that Minecraft/Discord pair to authenticate again.\n\n");
        for (AuthenticatedAccount account : byMinecraftUuid.values()) {
            builder.append("[[Account]]\n");
            builder.append("MinecraftUuid = ").append(quote(account.minecraftUuid())).append('\n');
            builder.append("MinecraftName = ").append(quote(account.minecraftName())).append('\n');
            builder.append("DiscordUserId = ").append(quote(account.discordUserId())).append('\n');
            builder.append("DiscordName = ").append(quote(account.discordName())).append('\n');
            builder.append("AuthenticatedAt = ").append(quote(account.authenticatedAt())).append("\n\n");
        }
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private static AuthenticatedAccount accountFrom(Map<String, String> values) {
        String minecraftUuid = values.getOrDefault("MinecraftUuid", "");
        String minecraftName = values.getOrDefault("MinecraftName", "");
        String discordUserId = values.getOrDefault("DiscordUserId", "");
        String discordName = values.getOrDefault("DiscordName", "");
        String authenticatedAt = values.getOrDefault("AuthenticatedAt", "");
        if (!BridgeConfig.hasText(minecraftUuid) || !BridgeConfig.hasText(discordUserId)) {
            return null;
        }
        return new AuthenticatedAccount(minecraftUuid, minecraftName, discordUserId, discordName, authenticatedAt);
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

    public record AuthenticatedAccount(
            String minecraftUuid,
            String minecraftName,
            String discordUserId,
            String discordName,
            String authenticatedAt
    ) {
    }
}
