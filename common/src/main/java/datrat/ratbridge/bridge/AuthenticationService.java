package datrat.ratbridge.bridge;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    public synchronized AuthenticationDecision checkLogin(
            BridgeConfig config,
            AuthenticationLoginContext login,
            DiscordBridgeClient discordClient
    ) {
        AuthenticationConfig authentication = config.authentication();
        if (!authentication.enabled() || store == null) {
            return AuthenticationDecision.allow();
        }

        if (bypassesAuthentication(authentication, login)) {
            return AuthenticationDecision.allow();
        }

        Optional<AuthenticationStore.AuthenticatedAccount> account = store.findByMinecraftUuid(login.minecraftUuid());
        if (account.isPresent()) {
            return verifyLinkedAccount(config, authentication, login, account.get(), discordClient);
        }

        String code = codeFor(login.minecraftUuid(), login.minecraftName(), authentication);
        String message = MessageFormatter.format(authentication.kickMessage(), CommonPlaceholders.withRatBridgeVersion(Map.of(
                "player", login.minecraftName(),
                "uuid", login.minecraftUuid(),
                "code", code,
                "logoutCommand", authentication.logoutCommand(),
                "logoutcommand", authentication.logoutCommand()
        )));
        return AuthenticationDecision.deny(message);
    }

    private boolean bypassesAuthentication(AuthenticationConfig authentication, AuthenticationLoginContext login) {
        String playerName = login.minecraftName().trim().toLowerCase(Locale.ROOT);
        boolean bypassName = authentication.bypassNames().stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .anyMatch(playerName::equals);
        if (bypassName) {
            return true;
        }
        if (authentication.whitelistedPlayersBypass() && login.whitelisted()) {
            return true;
        }
        if (authentication.onlyCheckBannedPlayers()) {
            return !login.banned();
        }
        return !authentication.checkBannedPlayers() && login.banned();
    }

    private AuthenticationDecision verifyLinkedAccount(
            BridgeConfig config,
            AuthenticationConfig authentication,
            AuthenticationLoginContext login,
            AuthenticationStore.AuthenticatedAccount account,
            DiscordBridgeClient discordClient
    ) {
        if (!authentication.requiresDiscordAccessCheck()) {
            return AuthenticationDecision.allow();
        }
        if (discordClient == null) {
            return AuthenticationDecision.deny(formatAccessMessage(authentication.roleCheckFailedMessage(), authentication, login, account));
        }
        try {
            AuthenticationAccessResult result = discordClient.verifyAuthenticationAccess(config, account).get(5, TimeUnit.SECONDS);
            return result.allowed()
                    ? AuthenticationDecision.allow()
                    : AuthenticationDecision.deny(formatAccessMessage(result.denyMessage(), authentication, login, account));
        } catch (Exception error) {
            return AuthenticationDecision.deny(formatAccessMessage(authentication.roleCheckFailedMessage(), authentication, login, account));
        }
    }

    private String formatAccessMessage(
            String template,
            AuthenticationConfig authentication,
            AuthenticationLoginContext login,
            AuthenticationStore.AuthenticatedAccount account
    ) {
        return MessageFormatter.format(template, CommonPlaceholders.withRatBridgeVersion(Map.of(
                "player", login.minecraftName(),
                "uuid", login.minecraftUuid(),
                "discord", account.discordName(),
                "invite", authentication.discordInvite()
        )));
    }

    public synchronized Optional<AuthenticationMessageResponse> handlePrivateMessage(BridgeConfig config, DiscordInboundMessage message) {
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
        return Optional.of(AuthenticationMessageResponse.reply(handleCode(authentication, message, content)));
    }

    private AuthenticationMessageResponse handleLogout(AuthenticationConfig authentication, DiscordInboundMessage message) {
        try {
            Optional<AuthenticationStore.AuthenticatedAccount> removed = store.unlinkByDiscordUserId(message.authorId());
            if (removed.isEmpty()) {
                return AuthenticationMessageResponse.reply(MessageFormatter.format(
                        authentication.logoutNotLinkedMessage(),
                        CommonPlaceholders.withRatBridgeVersion(Map.of("discord", message.author()))
                ));
            }
            AuthenticationStore.AuthenticatedAccount account = removed.get();
            String logoutMessage = MessageFormatter.format(
                    authentication.logoutSuccessMessage(),
                    CommonPlaceholders.withRatBridgeVersion(Map.of(
                            "player", account.minecraftName(),
                            "uuid", account.minecraftUuid(),
                            "discord", message.author()
                    ))
            );
            return AuthenticationMessageResponse.logout(
                    logoutMessage,
                    new AuthenticationLogout(account.minecraftUuid(), account.minecraftName(), logoutMessage)
            );
        } catch (IOException error) {
            return AuthenticationMessageResponse.reply("RatBridge could not update the authentication database. Check the server log.");
        }
    }

    private String handleCode(AuthenticationConfig authentication, DiscordInboundMessage message, String code) {
        PendingAuthentication pending = pendingByCode.remove(code);
        if (pending == null || pending.expired()) {
            return MessageFormatter.format(
                    authentication.invalidCodeMessage(),
                    CommonPlaceholders.withRatBridgeVersion(Map.of("discord", message.author(), "code", code))
            );
        }
        pendingCodeByMinecraftUuid.remove(pending.minecraftUuid());

        if (store.isMinecraftLinkedToOtherDiscord(pending.minecraftUuid(), message.authorId())
                || store.isDiscordLinkedToOtherMinecraft(message.authorId(), pending.minecraftUuid())) {
            return MessageFormatter.format(authentication.alreadyLinkedMessage(), CommonPlaceholders.withRatBridgeVersion(Map.of(
                    "player", pending.minecraftName(),
                    "discord", message.author(),
                    "code", code
            )));
        }

        try {
            store.link(pending.minecraftUuid(), pending.minecraftName(), message.authorId(), message.author());
        } catch (IOException error) {
            return "RatBridge could not update the authentication database. Check the server log.";
        }

        return MessageFormatter.format(authentication.successMessage(), CommonPlaceholders.withRatBridgeVersion(Map.of(
                "player", pending.minecraftName(),
                "discord", message.author(),
                "code", code
        )));
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
