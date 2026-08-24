package io.github.kenvandine.flowerwatch;

import java.time.Clock;
import java.time.Instant;

/**
 * Fixed-window rate limiter: allows up to {@code maxPerWindow} permits
 * per {@code windowSeconds}-second window, then refuses further permits
 * until the window rolls over. Plain Java, no Bukkit types, so it's
 * directly unit-testable with an injected {@link Clock}.
 *
 * Used to cap how often FlowerWatch queries CoreProtect's database — a
 * runaway flower-spawn burst (the exact thing this plugin exists to
 * catch) could otherwise also mean a burst of DB lookups hammering
 * CoreProtect, which would make the bug worse while trying to diagnose
 * it.
 */
public final class FixedWindowRateLimiter {

    private final int maxPerWindow;
    private final long windowSeconds;
    private final Clock clock;

    private Instant windowStart;
    private int usedInWindow;

    public FixedWindowRateLimiter(int maxPerWindow, long windowSeconds, Clock clock) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        this.maxPerWindow = maxPerWindow;
        this.windowSeconds = windowSeconds;
        this.clock = clock;
        this.windowStart = clock.instant();
    }

    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (now.isAfter(windowStart.plusSeconds(windowSeconds))) {
            windowStart = now;
            usedInWindow = 0;
        }
        if (usedInWindow >= maxPerWindow) {
            return false;
        }
        usedInWindow++;
        return true;
    }
}
