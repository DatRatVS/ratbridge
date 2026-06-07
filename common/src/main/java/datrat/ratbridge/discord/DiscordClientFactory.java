package datrat.ratbridge.discord;

import datrat.ratbridge.bridge.BridgeConfig;
import datrat.ratbridge.bridge.DiscordBridgeClient;

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
