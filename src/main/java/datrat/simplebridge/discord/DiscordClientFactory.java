package datrat.simplebridge.discord;

import datrat.simplebridge.bridge.BridgeConfig;
import datrat.simplebridge.bridge.DiscordBridgeClient;

public final class DiscordClientFactory {
    private DiscordClientFactory() {
    }

    public static DiscordBridgeClient create(BridgeConfig config) {
        return switch (config.normalizedMode()) {
            case "bot" -> new JdaDiscordBotClient();
            case "selfbot" -> new SelfbotDiscordClient();
            default -> throw new IllegalArgumentException("Unsupported Discord mode: " + config.mode());
        };
    }
}
