package datrat.ratbridge.platform.fabric;

import datrat.ratbridge.bridge.ServerStatusProvider;
import datrat.ratbridge.bridge.ServerStatusSnapshot;
import datrat.ratbridge.bridge.TpsMonitor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FabricServerStatusProvider implements ServerStatusProvider {
    private final MinecraftServer server;
    private final TpsMonitor tpsMonitor;

    public FabricServerStatusProvider(MinecraftServer server, TpsMonitor tpsMonitor) {
        this.server = server;
        this.tpsMonitor = tpsMonitor;
    }

    @Override
    public ServerStatusSnapshot snapshot(long uptimeMillis) {
        if (server.isSameThread()) {
            return readSnapshot(uptimeMillis);
        }

        CompletableFuture<ServerStatusSnapshot> future = new CompletableFuture<>();
        server.execute(() -> future.complete(readSnapshot(uptimeMillis)));
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return fallbackSnapshot(uptimeMillis);
        }
    }

    @Override
    public List<String> onlinePlayerNames() {
        if (server.isSameThread()) {
            return readOnlinePlayerNames();
        }

        CompletableFuture<List<String>> future = new CompletableFuture<>();
        server.execute(() -> future.complete(readOnlinePlayerNames()));
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> readOnlinePlayerNames() {
        return server.getPlayerList().getPlayers().stream()
                .map(player -> player.getGameProfile().getName())
                .collect(Collectors.toList());
    }

    private ServerStatusSnapshot readSnapshot(long uptimeMillis) {
        return new ServerStatusSnapshot(
                server.getPlayerList().getPlayerCount(),
                server.getPlayerList().getMaxPlayers(),
                totalPlayers(),
                server.getMotd(),
                server.getServerVersion(),
                tpsMonitor.averageTps(),
                uptimeMillis
        );
    }

    private ServerStatusSnapshot fallbackSnapshot(long uptimeMillis) {
        return new ServerStatusSnapshot(0, 0, totalPlayers(), "", "", tpsMonitor.averageTps(), uptimeMillis);
    }

    private long totalPlayers() {
        Path playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.notExists(playerData)) {
            return 0L;
        }
        try (Stream<Path> files = Files.list(playerData)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".dat")).count();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
