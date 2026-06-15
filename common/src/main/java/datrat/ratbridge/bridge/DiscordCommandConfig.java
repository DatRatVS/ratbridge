package datrat.ratbridge.bridge;

public record DiscordCommandConfig(
        boolean enabled,
        String onlineCommand,
        int onlineDeleteAfterSeconds,
        String onlinePlayersMessage,
        String onlineNoPlayersMessage
) {
    public static DiscordCommandConfig defaults() {
        return new DiscordCommandConfig(
                true,
                "r!online",
                10,
                "**{playercount} online player{playerPlural}:** {players}",
                "**No online players.**"
        );
    }
}
