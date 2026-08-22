package io.github.phqen1x.worldeditcraft.schem;

import java.util.List;

/**
 * Descriptive metadata written into a {@code .schem}'s {@code Metadata}
 * compound and kept in the library's JSON sidecar (see the design doc's
 * "Storage layout") — everything a schematic needs to be found, trusted,
 * and re-generated later. {@code prompt}, {@code model}, {@code tags}
 * and {@code checksum} aren't part of the Sponge v3 spec itself and are
 * WorldEditCraft-specific extensions to the sidecar only.
 */
public record SchematicMeta(
        String name,
        String author,
        long dateEpochMillis,
        String prompt,
        String model,
        List<String> tags,
        String checksum
) {
    public SchematicMeta {
        tags = List.copyOf(tags);
    }
}
