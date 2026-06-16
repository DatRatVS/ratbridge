package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class MessageFormatterTest {
    @Test
    void replacesKnownPlaceholders() {
        String formatted = MessageFormatter.format("[MC] <{player}> {message}", Map.of(
                "player", "Steve",
                "message", "Hello"
        ));

        assertEquals("[MC] <Steve> Hello", formatted);
    }

    @Test
    void replacesPercentPlaceholders() {
        String formatted = MessageFormatter.format("[MC] <%player%> %message%", Map.of(
                "player", "Steve",
                "message", "Hello"
        ));

        assertEquals("[MC] <Steve> Hello", formatted);
    }

    @Test
    void sanitizesDiscordMassMentions() {
        String sanitized = MentionSanitizer.sanitize("@everyone hi @here");

        assertFalse(sanitized.contains("@everyone"));
        assertFalse(sanitized.contains("@here"));
        assertEquals("@\u200Beveryone hi @\u200Bhere", sanitized);
    }

    @Test
    void normalizesAmpersandMinecraftFormattingCodes() {
        assertEquals("\u00A7aGreen \u00A7lbold", LegacyMinecraftFormat.normalizeCodes("&aGreen &lbold"));
        assertEquals("\u00A7cRed", LegacyMinecraftFormat.normalizeCodes("\u00A7cRed"));
        assertEquals("R&zD", LegacyMinecraftFormat.normalizeCodes("R&zD"));
    }

    @Test
    void stripsMinecraftFormattingCodes() {
        assertEquals("Green bold", LegacyMinecraftFormat.stripCodes("&aGreen &lbold"));
        assertEquals("Red reset", LegacyMinecraftFormat.stripCodes("\u00A7cRed \u00A7rreset"));
        assertEquals("R&zD", LegacyMinecraftFormat.stripCodes("R&zD"));
    }
}
