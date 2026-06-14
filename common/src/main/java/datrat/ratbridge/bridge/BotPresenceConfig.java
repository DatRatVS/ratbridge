package datrat.ratbridge.bridge;

public record BotPresenceConfig(
        String onlineStatus,
        String activityType,
        String activity,
        String streamUrl,
        int updateIntervalSeconds
) {
}
