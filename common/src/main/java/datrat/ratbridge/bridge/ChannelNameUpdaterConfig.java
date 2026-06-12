package datrat.ratbridge.bridge;

public record ChannelNameUpdaterConfig(
        String channelId,
        String message,
        String shutdownMessage,
        int updateIntervalMinutes
) {
}
