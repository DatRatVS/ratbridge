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
        boolean syncChat,
        boolean syncPlayerJoin,
        boolean syncPlayerLeave,
        boolean syncServerStart,
        boolean syncServerStop,
        int selfbotPollIntervalMillis,
        String minecraftToDiscordFormat,
        String discordToMinecraftFormat,
        String eventFormat
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

        if ("selfbot".equals(resolvedMode) && selfbotPollIntervalMillis < 500) {
            errors.add("selfbotPollIntervalMillis must be at least 500");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.invalid(errors);
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
