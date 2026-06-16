package datrat.ratbridge.bridge;

import datrat.ratbridge.RatBridgeInfo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TopicTemplateFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TopicTemplateFormatter() {
    }

    public static String format(String template, ServerStatusSnapshot status) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        Instant now = Instant.now();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneId.systemDefault());

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("%playercount%", Integer.toString(status.playerCount()));
        placeholders.put("%playermax%", Integer.toString(status.playerMax()));
        placeholders.put("%totalplayers%", Long.toString(status.totalPlayers()));
        placeholders.put("%uptimemins%", Long.toString(status.uptimeMillis() / 60_000L));
        placeholders.put("%uptimehours%", Long.toString(status.uptimeMillis() / 3_600_000L));
        placeholders.put("%motd%", status.motd());
        placeholders.put("%serverversion%", status.serverVersion());
        placeholders.put("%ratbridgeversion%", RatBridgeInfo.VERSION);
        placeholders.put("%tps%", String.format(Locale.ROOT, "%.2f", status.tps()));
        placeholders.put("%date%", DATE.format(LocalDate.from(localNow)));
        placeholders.put("%time%", TIME.format(LocalTime.from(localNow)));
        placeholders.put("%datetime%", DATE_TIME.format(localNow));
        placeholders.put("%timestamp%", Long.toString(now.getEpochSecond()));
        placeholders.put("%freememory%", Long.toString(toMegabytes(freeMemory)));
        placeholders.put("%usedmemory%", Long.toString(toMegabytes(usedMemory)));
        placeholders.put("%totalmemory%", Long.toString(toMegabytes(totalMemory)));
        placeholders.put("%maxmemory%", Long.toString(toMegabytes(maxMemory)));
        placeholders.put("%freememorygb%", toGigabytes(freeMemory));
        placeholders.put("%usedmemorygb%", toGigabytes(usedMemory));
        placeholders.put("%totalmemorygb%", toGigabytes(totalMemory));
        placeholders.put("%maxmemorygb%", toGigabytes(maxMemory));

        String formatted = template;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            formatted = formatted.replace(placeholder.getKey(), placeholder.getValue());
        }
        return formatted;
    }

    private static long toMegabytes(long bytes) {
        return bytes / 1_048_576L;
    }

    private static String toGigabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f", bytes / 1_073_741_824.0);
    }
}
