package datrat.ratbridge.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public record BridgeConfig(
        boolean enabled,
        String client,
        String mode,
        String token,
        String tokenEnv,
        String serverId,
        String channelId,
        boolean enableSelfbot,
        boolean webhookDelivery,
        String webhookName,
        boolean topicUpdaterEnabled,
        String topicUpdaterChannelId,
        String topicUpdaterMessage,
        String topicUpdaterShutdownMessage,
        int topicUpdaterIntervalMinutes,
        List<ChannelNameUpdaterConfig> channelNameUpdaters,
        boolean syncChat,
        boolean syncMinecraftToDiscordChat,
        boolean syncDiscordToMinecraftChat,
        boolean syncPlayerJoin,
        boolean syncPlayerLeave,
        boolean syncPlayerDeath,
        boolean syncPlayerAdvancement,
        boolean syncServerStart,
        boolean syncServerStop,
        int selfbotPollIntervalMillis,
        String minecraftToDiscordFormat,
        String discordToMinecraftFormat,
        String eventFormat,
        String playerJoinMessage,
        String playerLeaveMessage,
        String playerDeathMessage,
        String playerAdvancementMessage,
        String serverStartMessage,
        String serverStopMessage
) {
    public String normalizedClient() {
        return normalize(client);
    }

    public String normalizedMode() {
        return normalize(mode);
    }

    public String resolvedToken(Function<String, String> env) {
        if (hasText(token)) {
            return token.trim();
        }
        if (hasText(tokenEnv)) {
            String fromEnv = env.apply(tokenEnv.trim());
            if (hasText(fromEnv)) {
                return fromEnv.trim();
            }
        }
        return "";
    }

    public ValidationResult validate(Function<String, String> env) {
        List<String> errors = new ArrayList<>();

        if (!enabled) {
            return ValidationResult.success();
        }

        if (!"discord".equals(normalizedClient())) {
            errors.add("client must be 'discord'");
        }

        String resolvedMode = normalizedMode();
        if (!"bot".equals(resolvedMode) && !"selfbot".equals(resolvedMode)) {
            errors.add("mode must be 'bot' or 'selfbot'");
        }

        if (!hasText(resolvedToken(env))) {
            errors.add("token is required; set token or tokenEnv");
        }

        if ("bot".equals(resolvedMode) && !hasText(serverId)) {
            errors.add("serverId is required when mode is 'bot'");
        }

        if (("bot".equals(resolvedMode) || "selfbot".equals(resolvedMode)) && !hasText(channelId)) {
            errors.add("channelId is required when mode is '" + resolvedMode + "'");
        }

        if ("selfbot".equals(resolvedMode) && !enableSelfbot) {
            errors.add("selfbot mode requires enableSelfbot = true; selfbots can violate Discord terms and can get the account banned");
        }

        if (webhookDelivery && !"bot".equals(resolvedMode)) {
            errors.add("webhookDelivery requires mode = 'bot'; it is not allowed with selfbot mode");
        }

        if (webhookDelivery && !hasText(webhookName)) {
            errors.add("webhookName is required when webhookDelivery is true");
        }

        if (topicUpdaterEnabled && !"bot".equals(resolvedMode)) {
            errors.add("topicUpdaterEnabled requires mode = 'bot'; Discord channel topics cannot be managed by selfbot mode");
        }

        if (topicUpdaterEnabled && !hasText(resolvedTopicUpdaterChannelId())) {
            errors.add("topicUpdaterChannelId or channelId is required when topicUpdaterEnabled is true");
        }

        if (topicUpdaterEnabled && !hasText(topicUpdaterMessage)) {
            errors.add("topicUpdaterMessage is required when topicUpdaterEnabled is true");
        }

        if (topicUpdaterEnabled && topicUpdaterIntervalMinutes < 5) {
            errors.add("topicUpdaterIntervalMinutes must be at least 5 to respect Discord rate limits");
        }

        if (!channelNameUpdaters.isEmpty() && !"bot".equals(resolvedMode)) {
            errors.add("channel name updaters require mode = 'bot'; Discord guild channels cannot be managed by selfbot mode");
        }

        for (int index = 0; index < channelNameUpdaters.size(); index++) {
            ChannelNameUpdaterConfig updater = channelNameUpdaters.get(index);
            String prefix = "channelNameUpdater" + (index + 1);
            if (!hasText(updater.channelId())) {
                errors.add(prefix + "ChannelId is required");
            }
            if (!hasText(updater.message())) {
                errors.add(prefix + "Message is required");
            }
            if (updater.updateIntervalMinutes() < 5) {
                errors.add(prefix + "UpdateInterval must be at least 5 to respect Discord rate limits");
            }
        }

        if ("selfbot".equals(resolvedMode) && selfbotPollIntervalMillis < 500) {
            errors.add("selfbotPollIntervalMillis must be at least 500");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.invalid(errors);
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String resolvedTopicUpdaterChannelId() {
        return hasText(topicUpdaterChannelId) ? topicUpdaterChannelId.trim() : channelId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
