package datrat.ratbridge.bridge;

import java.util.Map;

public final class MessageFormatter {
    private MessageFormatter() {
    }

    public static String format(String template, Map<String, String> values) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result
                    .replace("%" + entry.getKey() + "%", value)
                    .replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
}
