package io.github.phqen1x.worldeditcraft.library;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicIndexTest {

    @Test
    void searchMatchesByNameOrPrompt() {
        SchematicIndex index = new SchematicIndex();
        index.put(record("ruined_forge_hall", "a ruined dwarven forge hall", "chk1", 1000));
        index.put(record("wayside_shrine", "a small wayside shrine", "chk2", 2000));

        List<SchematicRecord> byName = index.search(new SchematicQuery("forge", null, 1, 10));
        assertEquals(1, byName.size());
        assertEquals("ruined_forge_hall", byName.get(0).name());

        List<SchematicRecord> byPrompt = index.search(new SchematicQuery("wayside", null, 1, 10));
        assertEquals(1, byPrompt.size());
        assertEquals("wayside_shrine", byPrompt.get(0).name());
    }

    @Test
    void searchFiltersByTag() {
        SchematicIndex index = new SchematicIndex();
        index.put(new SchematicRecord("a", "author", 1, "prompt", "model", List.of("dungeon"), "chk1", 1, 1, 1));
        index.put(new SchematicRecord("b", "author", 2, "prompt", "model", List.of("shrine"), "chk2", 1, 1, 1));

        List<SchematicRecord> dungeons = index.search(new SchematicQuery(null, "dungeon", 1, 10));
        assertEquals(1, dungeons.size());
        assertEquals("a", dungeons.get(0).name());
    }

    @Test
    void searchPagesNewestFirst() {
        SchematicIndex index = new SchematicIndex();
        for (int i = 0; i < 5; i++) {
            index.put(record("s" + i, "prompt " + i, "chk" + i, 1000 + i));
        }

        List<SchematicRecord> page1 = index.search(new SchematicQuery(null, null, 1, 2));
        assertEquals(List.of("s4", "s3"), names(page1));

        List<SchematicRecord> page2 = index.search(new SchematicQuery(null, null, 2, 2));
        assertEquals(List.of("s2", "s1"), names(page2));

        List<SchematicRecord> page3 = index.search(new SchematicQuery(null, null, 3, 2));
        assertEquals(List.of("s0"), names(page3));
    }

    @Test
    void puttingARecordWithAMatchingChecksumReplacesTheOlderEntryUnderItsOldName() {
        SchematicIndex index = new SchematicIndex();
        index.put(record("original_name", "prompt", "same-checksum", 1000));
        assertEquals(1, index.size());

        // Same checksum, different name — as if the same rasterized bytes
        // got saved again under a different requested name.
        index.put(record("different_name", "prompt", "same-checksum", 2000));

        assertEquals(1, index.size(), "the old entry sharing this checksum should have been replaced, not kept alongside the new one");
        assertTrue(index.get("different_name").isPresent());
        assertFalse(index.get("original_name").isPresent());
    }

    @Test
    void jsonRoundTripsThroughToJsonAndFromJson() {
        SchematicIndex index = new SchematicIndex();
        index.put(new SchematicRecord("hall", "Phqen1xWorldEditCraft", 12345, "a hall", "test-model",
                List.of("dungeon", "stone"), "abc123", 10, 5, 8));

        SchematicIndex readBack = SchematicIndex.fromJson(index.toJson());
        SchematicRecord record = readBack.get("hall").orElseThrow();
        assertEquals("hall", record.name());
        assertEquals(List.of("dungeon", "stone"), record.tags());
        assertEquals(10, record.width());
    }

    private static SchematicRecord record(String name, String prompt, String checksum, long date) {
        return new SchematicRecord(name, "author", date, prompt, "model", List.of(), checksum, 1, 1, 1);
    }

    private static List<String> names(List<SchematicRecord> records) {
        return records.stream().map(SchematicRecord::name).toList();
    }
}
