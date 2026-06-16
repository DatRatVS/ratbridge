package datrat.ratbridge.bridge;

import datrat.ratbridge.RatBridgeInfo;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CommonPlaceholders {
    private CommonPlaceholders() {
    }

    public static Map<String, String> withRatBridgeVersion(Map<String, String> values) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("ratbridgeversion", RatBridgeInfo.VERSION);
        merged.putAll(values);
        return merged;
    }
}
