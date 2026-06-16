package datrat.ratbridge.bridge;

import java.util.Locale;

public final class LegacyMinecraftFormat {
    private static final String VALID_CODES = "0123456789abcdefklmnor";

    private LegacyMinecraftFormat() {
    }

    public static String normalizeCodes(String message) {
        return rewrite(message, true, false);
    }

    public static String stripCodes(String message) {
        return rewrite(message, false, true);
    }

    private static String rewrite(String message, boolean normalizeAmpersand, boolean strip) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(message.length());
        for (int index = 0; index < message.length(); index++) {
            char current = message.charAt(index);
            if ((current == '&' || current == '\u00A7') && index + 1 < message.length()) {
                char code = Character.toLowerCase(message.charAt(index + 1));
                if (isFormattingCode(code)) {
                    if (!strip) {
                        result.append(normalizeAmpersand ? '\u00A7' : current);
                        result.append(code);
                    }
                    index++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private static boolean isFormattingCode(char code) {
        return VALID_CODES.contains(String.valueOf(code).toLowerCase(Locale.ROOT));
    }
}
