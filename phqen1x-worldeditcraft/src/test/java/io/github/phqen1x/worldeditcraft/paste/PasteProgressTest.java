package io.github.phqen1x.worldeditcraft.paste;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasteProgressTest {

    @Test
    void isCompleteWhenPlacedReachesTotal() {
        assertTrue(new PasteProgress(100, 100, 5000).isComplete());
        assertTrue(new PasteProgress(150, 100, 5000).isComplete());
        assertFalse(new PasteProgress(50, 100, 5000).isComplete());
    }

    @Test
    void percentCompleteRoundsDown() {
        assertEquals(33, new PasteProgress(1, 3, 1000).percentComplete());
        assertEquals(100, new PasteProgress(3, 3, 1000).percentComplete());
        assertEquals(0, new PasteProgress(0, 100, 1000).percentComplete());
    }

    @Test
    void etaExtrapolatesFromTheCurrentRate() {
        // 50 placed in 1000ms => 50/sec; 50 remaining => ~1000ms left.
        PasteProgress progress = new PasteProgress(50, 100, 1000);
        assertEquals(1000, progress.etaMillis());
    }

    @Test
    void etaIsUnknownBeforeAnyProgress() {
        assertEquals(-1, new PasteProgress(0, 100, 500).etaMillis());
    }

    @Test
    void etaIsUnknownOnceComplete() {
        assertEquals(-1, new PasteProgress(100, 100, 500).etaMillis());
    }
}
