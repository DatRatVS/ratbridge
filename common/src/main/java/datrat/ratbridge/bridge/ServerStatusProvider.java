package datrat.ratbridge.bridge;

public interface ServerStatusProvider {
    ServerStatusSnapshot snapshot(long uptimeMillis);

    static ServerStatusProvider empty() {
        return uptimeMillis -> new ServerStatusSnapshot(
                0,
                0,
                0,
                "",
                "",
                20.0,
                uptimeMillis
        );
    }
}
