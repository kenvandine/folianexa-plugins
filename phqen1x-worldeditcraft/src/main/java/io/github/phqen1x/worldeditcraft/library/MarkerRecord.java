package io.github.phqen1x.worldeditcraft.library;

import java.util.Map;

/**
 * A named anchor point saved alongside a schematic — the library's own
 * copy of a {@code marker} op's output (see the design doc's "Markers —
 * the entire RPG integration surface"). Sponge's {@code .schem} format
 * has no concept of markers, so these are persisted in a {@code
 * <name>.markers.json} sidecar next to the {@code .schem} file rather
 * than inside it, keeping the {@code .schem} itself a plain,
 * WorldEdit-interoperable file with nothing FoliaNexa-specific grafted
 * into it.
 */
public record MarkerRecord(String id, int x, int y, int z, Map<String, Object> meta) {
    public MarkerRecord {
        meta = Map.copyOf(meta);
    }
}
