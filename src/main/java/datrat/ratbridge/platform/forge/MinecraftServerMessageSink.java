package datrat.ratbridge.platform.forge;

import datrat.ratbridge.bridge.MinecraftMessageSink;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class MinecraftServerMessageSink implements MinecraftMessageSink {
    private final MinecraftServer server;

    public MinecraftServerMessageSink(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void sendSystemMessage(String message) {
        Runnable broadcast = () -> server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        if (server.isSameThread()) {
            broadcast.run();
        } else {
            server.execute(broadcast);
        }
    }
}
