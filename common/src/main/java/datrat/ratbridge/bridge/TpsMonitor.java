package datrat.ratbridge.bridge;

public final class TpsMonitor {
    private static final int WINDOW_SIZE = 100;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long[] intervals = new long[WINDOW_SIZE];
    private int nextIndex;
    private int count;
    private long lastTickNanos;

    public synchronized void recordTick() {
        long now = System.nanoTime();
        if (lastTickNanos != 0L) {
            intervals[nextIndex] = now - lastTickNanos;
            nextIndex = (nextIndex + 1) % intervals.length;
            if (count < intervals.length) {
                count++;
            }
        }
        lastTickNanos = now;
    }

    public synchronized double averageTps() {
        if (count == 0) {
            return 20.0;
        }
        long total = 0L;
        for (int index = 0; index < count; index++) {
            total += intervals[index];
        }
        double averageTickNanos = total / (double) count;
        if (averageTickNanos <= 0.0) {
            return 20.0;
        }
        return Math.min(20.0, NANOS_PER_SECOND / averageTickNanos);
    }
}
