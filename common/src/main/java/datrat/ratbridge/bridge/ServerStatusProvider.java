package datrat.ratbridge.bridge;

import java.util.List;

public interface ServerStatusProvider {
    ServerStatusSnapshot snapshot(long uptimeMillis);

    default List<String> onlinePlayerNames() {
        return List.of();
    }

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
