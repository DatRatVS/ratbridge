package datrat.ratbridge.bridge;

public record ServerStatusSnapshot(
        int playerCount,
        int playerMax,
        long totalPlayers,
        String motd,
        String serverVersion,
        double tps,
        long uptimeMillis
) {
}
