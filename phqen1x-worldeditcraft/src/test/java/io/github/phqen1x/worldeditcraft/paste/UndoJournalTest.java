package io.github.phqen1x.worldeditcraft.paste;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real file I/O against a real (JUnit-managed) temp directory — no mocking. */
class UndoJournalTest {

    @Test
    void writeAndReadRoundTrips(@TempDir Path tempDir) {
        List<UndoJournal.Entry> entries = List.of(
                new UndoJournal.Entry(1, 64, 2, "minecraft:air"),
                new UndoJournal.Entry(1, 65, 2, "minecraft:stone")
        );
        Path file = tempDir.resolve("job.json");

        UndoJournal.write(file, entries);
        List<UndoJournal.Entry> readBack = UndoJournal.read(file);

        assertEquals(entries, readBack);
    }

    @Test
    void writeCreatesParentDirectories(@TempDir Path tempDir) {
        Path file = tempDir.resolve("nested").resolve("world").resolve("job.json");
        UndoJournal.write(file, List.of(new UndoJournal.Entry(0, 0, 0, "minecraft:air")));
        assertTrue(java.nio.file.Files.exists(file));
    }

    @Test
    void replayOrderReversesTheEntryList() {
        List<UndoJournal.Entry> entries = List.of(
                new UndoJournal.Entry(0, 0, 0, "minecraft:air"),   // pass 1 wrote air over original grass
                new UndoJournal.Entry(0, 0, 0, "minecraft:air")    // pass 2 then overwrote that air with a torch (recorded state: air)
        );
        List<UndoJournal.Entry> replay = UndoJournal.replayOrder(entries);
        assertEquals(List.of(entries.get(1), entries.get(0)), replay);
    }

    @Test
    void replayOrderRestoresTheOriginalStateWhenAPositionWasOverwrittenTwice() {
        // Position (5,64,5) started as "minecraft:grass_block". Pass 1
        // overwrote it with "minecraft:stone" (recording the original
        // grass_block as the undo entry). Pass 2 then overwrote that
        // stone with "minecraft:torch" (recording stone as its own undo
        // entry). Replaying in reverse must apply stone first, then
        // grass_block last, so the final state is the original block.
        List<UndoJournal.Entry> entries = List.of(
                new UndoJournal.Entry(5, 64, 5, "minecraft:grass_block"),
                new UndoJournal.Entry(5, 64, 5, "minecraft:stone")
        );
        List<UndoJournal.Entry> replay = UndoJournal.replayOrder(entries);
        assertEquals("minecraft:stone", replay.get(0).block());
        assertEquals("minecraft:grass_block", replay.get(1).block());
    }

    @Test
    void readingAMissingFileThrows(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nonexistent.json");
        org.junit.jupiter.api.Assertions.assertThrows(java.io.UncheckedIOException.class, () -> UndoJournal.read(missing));
    }
}
