package io.github.phqen1x.worldeditcraft.paste;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasteJobTest {

    @Test
    void tracksPlacedCountAcrossMultipleUpdates() {
        PasteJob job = new PasteJob(100);
        job.addPlaced(30);
        job.addPlaced(20);
        assertEquals(50, job.progress().placed());
        assertEquals(100, job.progress().total());
    }

    @Test
    void startsNotCancelled() {
        assertFalse(new PasteJob(10).isCancelled());
    }

    @Test
    void cancelIsObservedAfterward() {
        PasteJob job = new PasteJob(10);
        job.cancel();
        assertTrue(job.isCancelled());
    }

    @Test
    void eachJobHasAUniqueId() {
        PasteJob a = new PasteJob(1);
        PasteJob b = new PasteJob(1);
        assertNotEquals(a.id(), b.id());
    }
}
