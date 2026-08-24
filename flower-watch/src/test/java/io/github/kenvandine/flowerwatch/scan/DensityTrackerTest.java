package io.github.kenvandine.flowerwatch.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DensityTrackerTest {

    private final ChunkKey key = new ChunkKey("world", 3, -4);

    @Test
    void firstScanOfAChunkNeverReportsADelta() {
        DensityTracker tracker = new DensityTracker();
        assertTrue(tracker.recordAndDelta(key, 500).isEmpty(), "pre-existing density shouldn't look like a burst");
    }

    @Test
    void aGrowingCountReportsTheIncrease() {
        DensityTracker tracker = new DensityTracker();
        tracker.recordAndDelta(key, 10);
        var delta = tracker.recordAndDelta(key, 37);
        assertTrue(delta.isPresent());
        assertEquals(27, delta.get());
    }

    @Test
    void aShrinkingOrUnchangedCountReportsNoDelta() {
        DensityTracker tracker = new DensityTracker();
        tracker.recordAndDelta(key, 50);
        assertFalse(tracker.recordAndDelta(key, 50).isPresent());
        assertFalse(tracker.recordAndDelta(key, 12).isPresent());
    }

    @Test
    void differentChunksAreTrackedIndependently() {
        DensityTracker tracker = new DensityTracker();
        ChunkKey other = new ChunkKey("world", 3, -5);
        tracker.recordAndDelta(key, 10);
        tracker.recordAndDelta(other, 999);

        var delta = tracker.recordAndDelta(key, 15);
        assertTrue(delta.isPresent());
        assertEquals(5, delta.get());
        assertEquals(2, tracker.size());
    }
}
