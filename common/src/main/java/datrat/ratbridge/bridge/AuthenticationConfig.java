package datrat.ratbridge.bridge;

public record AuthenticationConfig(
        boolean enabled,
        int codeTtlMinutes,
        String kickMessage,
        String successMessage,
        String invalidCodeMessage,
        String alreadyLinkedMessage,
        String logoutCommand,
        String logoutSuccessMessage,
        String logoutNotLinkedMessage
) {
    public static AuthenticationConfig disabled() {
        return new AuthenticationConfig(
                false,
                10,
                "This server requires Discord authentication. Send code {code} to the RatBridge bot DM to authenticate {player}.",
                "Authenticated {player}. You can now join the server.",
                "Invalid or expired authentication code.",
                "That Minecraft or Discord account is already linked to another account.",
                "r!logout",
                "Your Minecraft account link was removed. Join the server again to get a new code.",
                "Your Discord account is not linked to any Minecraft account."
        );
    }
}
