package datrat.ratbridge.platform.neoforge;

import datrat.ratbridge.bridge.MinecraftMessageSink;
import net.minecraft.server.MinecraftServer;

public final class NeoForgeMinecraftMessageSink implements MinecraftMessageSink {
    private final MinecraftServer server;

    public NeoForgeMinecraftMessageSink(MinecraftServer server) {
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
