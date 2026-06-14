package datrat.ratbridge.bridge;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class AuthenticationService {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingAuthentication> pendingByCode = new HashMap<>();
    private final Map<String, String> pendingCodeByMinecraftUuid = new HashMap<>();
    private AuthenticationStore store;

    public synchronized void start(AuthenticationStore store) throws IOException {
        this.store = store;
        this.store.load();
        pendingByCode.clear();
        pendingCodeByMinecraftUuid.clear();
    }

    public synchronized void stop() {
        store = null;
        pendingByCode.clear();
        pendingCodeByMinecraftUuid.clear();
    }

    public synchronized AuthenticationDecision checkLogin(BridgeConfig config, String minecraftUuid, String minecraftName) {
        AuthenticationConfig authentication = config.authentication();
        if (!authentication.enabled() || store == null) {
            return AuthenticationDecision.allow();
        }

        if (store.findByMinecraftUuid(minecraftUuid).isPresent()) {
            return AuthenticationDecision.allow();
        }

        String code = codeFor(minecraftUuid, minecraftName, authentication);
        String message = MessageFormatter.format(authentication.kickMessage(), Map.of(
                "player", minecraftName,
                "uuid", minecraftUuid,
                "code", code,
                "logoutCommand", authentication.logoutCommand()
        ));
        return AuthenticationDecision.deny(message);
    }

    public synchronized Optional<String> handlePrivateMessage(BridgeConfig config, DiscordInboundMessage message) {
        AuthenticationConfig authentication = config.authentication();
        if (!authentication.enabled() || store == null || !BridgeConfig.hasText(message.authorId())) {
            return Optional.empty();
        }

        String content = message.content().trim();
        if (content.equalsIgnoreCase(authentication.logoutCommand().trim())) {
            return Optional.of(handleLogout(authentication, message));
        }
        if (!content.matches("\\d{6}")) {
            return Optional.empty();
        }
        return Optional.of(handleCode(authentication, message, content));
    }

    private String handleLogout(AuthenticationConfig authentication, DiscordInboundMessage message) {
        try {
            boolean removed = store.unlinkByDiscordUserId(message.authorId());
            return removed ? authentication.logoutSuccessMessage() : authentication.logoutNotLinkedMessage();
        } catch (IOException error) {
            return "RatBridge could not update the authentication database. Check the server log.";
        }
    }

    private String handleCode(AuthenticationConfig authentication, DiscordInboundMessage message, String code) {
        PendingAuthentication pending = pendingByCode.remove(code);
        if (pending == null || pending.expired()) {
            return authentication.invalidCodeMessage();
        }
        pendingCodeByMinecraftUuid.remove(pending.minecraftUuid());

        if (store.isMinecraftLinkedToOtherDiscord(pending.minecraftUuid(), message.authorId())
                || store.isDiscordLinkedToOtherMinecraft(message.authorId(), pending.minecraftUuid())) {
            return MessageFormatter.format(authentication.alreadyLinkedMessage(), Map.of(
                    "player", pending.minecraftName(),
                    "discord", message.author(),
                    "code", code
            ));
        }

        try {
            store.link(pending.minecraftUuid(), pending.minecraftName(), message.authorId(), message.author());
        } catch (IOException error) {
            return "RatBridge could not update the authentication database. Check the server log.";
        }

        return MessageFormatter.format(authentication.successMessage(), Map.of(
                "player", pending.minecraftName(),
                "discord", message.author(),
                "code", code
        ));
    }

    private String codeFor(String minecraftUuid, String minecraftName, AuthenticationConfig config) {
        cleanupExpired();
        String existingCode = pendingCodeByMinecraftUuid.get(minecraftUuid);
        PendingAuthentication existing = existingCode == null ? null : pendingByCode.get(existingCode);
        if (existing != null && !existing.expired()) {
            return existingCode;
        }
        if (existingCode != null) {
            pendingByCode.remove(existingCode);
            pendingCodeByMinecraftUuid.remove(minecraftUuid);
        }

        String code;
        do {
            code = "%06d".formatted(random.nextInt(1_000_000));
        } while (pendingByCode.containsKey(code));

        long expiresAtMillis = System.currentTimeMillis() + Duration.ofMinutes(Math.max(1, config.codeTtlMinutes())).toMillis();
        pendingByCode.put(code, new PendingAuthentication(minecraftUuid, minecraftName, expiresAtMillis));
        pendingCodeByMinecraftUuid.put(minecraftUuid, code);
        return code;
    }

    private void cleanupExpired() {
        pendingByCode.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expired();
            if (expired) {
                pendingCodeByMinecraftUuid.remove(entry.getValue().minecraftUuid(), entry.getKey());
            }
            return expired;
        });
    }

    private record PendingAuthentication(String minecraftUuid, String minecraftName, long expiresAtMillis) {
        private boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
