package datrat.ratbridge.bridge;

public record AuthenticationLoginContext(
        String minecraftUuid,
        String minecraftName,
        boolean whitelisted,
        boolean banned
) {
}
