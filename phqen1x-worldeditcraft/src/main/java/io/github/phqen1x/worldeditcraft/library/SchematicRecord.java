package io.github.phqen1x.worldeditcraft.library;

import java.util.List;

/**
 * One library catalogue entry — everything {@code /wec list}/{@code
 * info} need, and everything {@code index.json} persists per schematic.
 * Mirrors {@code SchematicMeta} plus the dimensions a listing wants to
 * show without re-opening the {@code .schem} file itself.
 */
public record SchematicRecord(
        String name,
        String author,
        long dateEpochMillis,
        String prompt,
        String model,
        List<String> tags,
        String checksum,
        int width,
        int height,
        int length
) {
    public SchematicRecord {
        tags = List.copyOf(tags);
    }
}
