package io.github.kenvandine.flowerwatch;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWindowRateLimiterTest {

    /** A mutable {@link Clock} so tests can move time forward deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsUpToTheLimitWithinAWindow() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 60, clock);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "a 4th permit within the same window should be refused");
    }

    @Test
    void resetsOnceTheWindowRollsOver() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, 60, clock);

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        clock.advanceSeconds(61);
        assertTrue(limiter.tryAcquire(), "a new window should grant permits again");
    }

    @Test
    void zeroMaxPerWindowNeverAllowsAnything() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(0, 60, clock);

        assertFalse(limiter.tryAcquire());
    }
}
