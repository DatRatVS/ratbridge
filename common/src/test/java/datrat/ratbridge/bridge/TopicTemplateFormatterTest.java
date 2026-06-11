package datrat.ratbridge.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TopicTemplateFormatterTest {
    @Test
    void formatsServerStatusPlaceholders() {
        ServerStatusSnapshot status = new ServerStatusSnapshot(
                4,
                20,
                12,
                "Rat SMP",
                "Forge-1.20.1",
                19.876,
                3_660_000L
        );

        String formatted = TopicTemplateFormatter.format(
                "%playercount%/%playermax% %totalplayers% %motd% %serverversion% %tps% %uptimemins% %uptimehours%",
                status
        );

        assertTrue(formatted.contains("4/20"));
        assertTrue(formatted.contains("12"));
        assertTrue(formatted.contains("Rat SMP"));
        assertTrue(formatted.contains("Forge-1.20.1"));
        assertTrue(formatted.contains("19.88"));
        assertTrue(formatted.contains("61"));
        assertTrue(formatted.contains("1"));
    }
}
