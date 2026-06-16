package datrat.ratbridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RatBridgeInfoTest {
    @Test
    void exposesGeneratedModVersionAndUserAgent() {
        assertFalse(RatBridgeInfo.VERSION.isBlank());
        assertEquals(RatBridgeInfo.MOD_NAME + "/" + RatBridgeInfo.VERSION, RatBridgeInfo.USER_AGENT);
    }
}
