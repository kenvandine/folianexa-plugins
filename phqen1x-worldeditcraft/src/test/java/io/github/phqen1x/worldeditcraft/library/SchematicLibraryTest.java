package io.github.phqen1x.worldeditcraft.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real file I/O against a real (JUnit-managed) temp directory — no mocking. */
class SchematicLibraryTest {

    @Test
    void savesLoadsAndListsARealSchematic(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        byte[] bytes = {1, 2, 3, 4};

        String slug = library.save("ruined_forge_hall", bytes, meta("ruined_forge_hall"), List.of());
        assertEquals("ruined_forge_hall", slug);

        assertArrayEquals(bytes, library.load("ruined_forge_hall").orElseThrow());
        assertTrue(Files.exists(tempDir.resolve("ruined_forge_hall.schem")));
        assertTrue(Files.exists(tempDir.resolve("index.json")));

        List<SchematicRecord> all = library.list(SchematicQuery.firstPage());
        assertEquals(1, all.size());
        assertEquals("ruined_forge_hall", all.get(0).name());
    }

    @Test
    void reopeningTheLibraryReloadsTheIndexFromDisk(@TempDir Path tempDir) {
        SchematicLibrary first = SchematicLibrary.open(tempDir);
        first.save("hall", new byte[]{9}, meta("hall"), List.of());

        SchematicLibrary reopened = SchematicLibrary.open(tempDir);
        assertEquals(1, reopened.size());
        assertTrue(reopened.info("hall").isPresent());
    }

    @Test
    void savingUnderACollidingNameWithDifferentContentGetsANumericSuffix(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        library.save("hall", new byte[]{1}, meta("hall", "checksum-a"), List.of());
        String secondSlug = library.save("hall", new byte[]{2}, meta("hall", "checksum-b"), List.of());

        assertEquals("hall_2", secondSlug);
        assertArrayEquals(new byte[]{1}, library.load("hall").orElseThrow());
        assertArrayEquals(new byte[]{2}, library.load("hall_2").orElseThrow());
    }

    @Test
    void deleteRemovesBothTheFileAndTheIndexEntry(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        library.save("hall", new byte[]{1}, meta("hall"), List.of());

        assertTrue(library.delete("hall"));
        assertFalse(Files.exists(tempDir.resolve("hall.schem")));
        assertTrue(library.info("hall").isEmpty());
        assertFalse(library.delete("hall")); // already gone
    }

    @Test
    void renameMovesTheFileAndUpdatesTheIndex(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        library.save("hall", new byte[]{1}, meta("hall"), List.of());

        assertTrue(library.rename("hall", "great_hall"));
        assertFalse(Files.exists(tempDir.resolve("hall.schem")));
        assertTrue(Files.exists(tempDir.resolve("great_hall.schem")));
        assertTrue(library.info("great_hall").isPresent());
        assertTrue(library.info("hall").isEmpty());
    }

    @Test
    void savesAndLoadsMarkersAlongsideTheSchematic(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        List<MarkerRecord> markers = List.of(
                new MarkerRecord("entrance", 4, 1, 0, java.util.Map.of()),
                new MarkerRecord("loot_1", 2, 1, 3, java.util.Map.of("rarity", "rare"))
        );

        library.save("hall", new byte[]{1}, meta("hall"), markers);

        assertTrue(Files.exists(tempDir.resolve("hall.markers.json")));
        List<MarkerRecord> loaded = library.loadMarkers("hall");
        assertEquals(2, loaded.size());
        assertEquals("entrance", loaded.get(0).id());
        assertEquals(4, loaded.get(0).x());
        assertEquals("rare", loaded.get(1).meta().get("rarity"));
    }

    @Test
    void loadMarkersIsEmptyWhenNoneWereSaved(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        library.save("hall", new byte[]{1}, meta("hall"), List.of());
        assertTrue(library.loadMarkers("hall").isEmpty());
    }

    @Test
    void renameMovesTheMarkersSidecarToo(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        library.save("hall", new byte[]{1}, meta("hall"), List.of(new MarkerRecord("entrance", 1, 1, 1, java.util.Map.of())));

        library.rename("hall", "great_hall");

        assertFalse(Files.exists(tempDir.resolve("hall.markers.json")));
        assertTrue(Files.exists(tempDir.resolve("great_hall.markers.json")));
        assertEquals(1, library.loadMarkers("great_hall").size());
    }

    @Test
    void deleteRemovesTheMarkersSidecarToo(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        library.save("hall", new byte[]{1}, meta("hall"), List.of(new MarkerRecord("entrance", 1, 1, 1, java.util.Map.of())));

        library.delete("hall");

        assertFalse(Files.exists(tempDir.resolve("hall.markers.json")));
    }

    @Test
    void loadingAMissingSchematicReturnsEmpty(@TempDir Path tempDir) {
        SchematicLibrary library = SchematicLibrary.open(tempDir);
        Optional<byte[]> result = library.load("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void slugifyLowercasesAndReplacesInvalidCharacters() {
        assertEquals("a_ruined_forge_hall", SchematicLibrary.slugify("A Ruined Forge Hall!"));
    }

    private static SchematicRecord meta(String name) {
        return meta(name, "checksum-" + name);
    }

    private static SchematicRecord meta(String name, String checksum) {
        return new SchematicRecord(name, "Phqen1xWorldEditCraft", 1_700_000_000_000L, "a test prompt", "test-model",
                List.of(), checksum, 4, 4, 4);
    }
}
