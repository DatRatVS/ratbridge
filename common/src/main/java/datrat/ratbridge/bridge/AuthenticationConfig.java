package datrat.ratbridge.bridge;

import java.util.Arrays;
import java.util.List;

public record AuthenticationConfig(
        boolean enabled,
        int codeTtlMinutes,
        List<String> bypassNames,
        boolean whitelistedPlayersBypass,
        boolean checkBannedPlayers,
        boolean onlyCheckBannedPlayers,
        String requiredDiscordServers,
        boolean requireSubscriberRole,
        List<String> subscriberRoles,
        boolean requireAllSubscriberRoles,
        String subscriberRoleKickMessage,
        String notInServerMessage,
        String missingSubscriberRoleMessage,
        String roleCheckFailedMessage,
        String discordInvite,
        boolean unauthenticatedLoginMessageEnabled,
        String unauthenticatedLoginMessage,
        String kickMessage,
        String successMessage,
        String invalidCodeMessage,
        String alreadyLinkedMessage,
        String logoutCommand,
        String logoutSuccessMessage,
        String logoutNotLinkedMessage
) {
    public boolean requiresDiscordAccessCheck() {
        return requiresDiscordServerCheck() || requireSubscriberRole;
    }

    public boolean requiresDiscordServerCheck() {
        return BridgeConfig.hasText(requiredDiscordServers)
                && !"false".equalsIgnoreCase(requiredDiscordServers.trim())
                && !"[]".equals(requiredDiscordServers.trim());
    }

    public boolean requiresAnyDiscordServer() {
        return requiresDiscordServerCheck() && "true".equalsIgnoreCase(requiredDiscordServers.trim());
    }

    public List<String> requiredDiscordServerIds(String fallbackServerId) {
        if (!requiresDiscordServerCheck()) {
            return List.of();
        }
        String raw = requiredDiscordServers.trim();
        if ("true".equalsIgnoreCase(raw)) {
            return BridgeConfig.hasText(fallbackServerId) ? List.of(fallbackServerId.trim()) : List.of();
        }
        return parseStringList(raw);
    }

    public static List<String> parseStringList(String raw) {
        if (!BridgeConfig.hasText(raw)) {
            return List.of();
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (!BridgeConfig.hasText(value)) {
            return List.of();
        }
        if (!value.contains(",")) {
            return List.of(stripQuotes(value));
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(AuthenticationConfig::stripQuotes)
                .filter(BridgeConfig::hasText)
                .toList();
    }

    private static String stripQuotes(String value) {
        String stripped = value.trim();
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            return stripped.substring(1, stripped.length() - 1);
        }
        return stripped;
    }

    public static AuthenticationConfig disabled() {
        return new AuthenticationConfig(
                false,
                10,
                List.of(),
                true,
                false,
                false,
                "false",
                false,
                List.of(),
                false,
                "You must have the required Discord role to join this server.",
                "You are not currently in the required Discord server.",
                "RatBridge could not find any configured subscriber role. Contact a server admin.",
                "RatBridge could not verify your Discord access. Contact a server admin.",
                "",
                true,
                "%player% tried to join but is not authenticated yet.",
                "This server requires Discord authentication. Send code %code% to the RatBridge bot DM to authenticate %player%.",
                "Authenticated %player%. You can now join the server.",
                "Invalid or expired authentication code.",
                "That Minecraft or Discord account is already linked to another account.",
                "r!logout",
                "Your Minecraft account link was removed. Join the server again to get a new code.",
                "Your Discord account is not linked to any Minecraft account."
        );
    }
}
