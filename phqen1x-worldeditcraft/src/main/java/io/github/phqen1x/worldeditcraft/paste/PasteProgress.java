package io.github.phqen1x.worldeditcraft.paste;

/** Placed / total / elapsed / ETA — backs the operator-facing progress report for an in-flight paste. */
public record PasteProgress(int placed, int total, long elapsedMillis) {

    public boolean isComplete() {
        return total <= 0 || placed >= total;
    }

    /** Milliseconds remaining at the current average rate, or {@code -1} if that can't yet be estimated. */
    public long etaMillis() {
        if (placed <= 0 || elapsedMillis <= 0 || isComplete()) {
            return -1;
        }
        double perMilli = (double) placed / elapsedMillis;
        return (long) ((total - placed) / perMilli);
    }

    public int percentComplete() {
        if (total <= 0) {
            return 100;
        }
        return (int) Math.min(100, (100L * placed) / total);
    }
}
