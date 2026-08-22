package io.github.phqen1x.worldeditcraft.dsl;

import java.util.List;
import java.util.Map;

/**
 * A parsed build script — the document {@link BuildScriptParser} produces
 * from raw JSON, before {@link BuildScriptValidator} clamps its size and
 * drops invalid ops. See docs/phqen1x-rpg-suite/03-buildscript-dsl.md for
 * the normative JSON shape this mirrors field-for-field.
 */
public record BuildScript(
        String name,
        int[] size,
        long seed,
        Map<String, String> palette,
        List<BuildOp> ops,
        String description
) {
    public BuildScript {
        size = size.clone();
        palette = Map.copyOf(palette);
        ops = List.copyOf(ops);
    }

    @Override
    public int[] size() {
        return size.clone();
    }
}
