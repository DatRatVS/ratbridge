package datrat.ratbridge.bridge;

public final class MentionSanitizer {
    private static final char ZERO_WIDTH_SPACE = '\u200B';

    private MentionSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return message
                .replace("@everyone", "@" + ZERO_WIDTH_SPACE + "everyone")
                .replace("@here", "@" + ZERO_WIDTH_SPACE + "here");
    }
}
