package datrat.ratbridge.platform.fabric;

import datrat.ratbridge.bridge.MinecraftMessageSink;
import net.minecraft.server.MinecraftServer;

public final class FabricMinecraftMessageSink implements MinecraftMessageSink {
    private final MinecraftServer server;

    public FabricMinecraftMessageSink(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void sendSystemMessage(String message) {
        Runnable broadcast = () -> server.getPlayerList().broadcastSystemMessage(LegacyTextComponents.parse(message), false);
        if (server.isSameThread()) {
            broadcast.run();
        } else {
            server.execute(broadcast);
        }
    }
}
