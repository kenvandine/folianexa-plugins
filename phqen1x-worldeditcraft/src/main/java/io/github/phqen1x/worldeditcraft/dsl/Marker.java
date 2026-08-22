package io.github.phqen1x.worldeditcraft.dsl;

import java.util.Map;

/**
 * A named anchor point recorded by a {@code marker} op — not a block.
 * This is the entire RPG integration surface (see the design doc's
 * "Markers" section): a caller pastes a schematic and gets back these
 * positions translated into world coordinates, without ever parsing
 * geometry itself. Reserved IDs ({@code entrance}, {@code spawn}, {@code
 * boss}, {@code loot}, {@code npc}, {@code mob}, {@code focus}) are
 * repeatable by convention (the model names successive ones {@code
 * loot_1}, {@code loot_2}, ...) — this type places no restriction on
 * {@code id} itself.
 */
public record Marker(String id, int[] position, Map<String, Object> meta) {
    public Marker {
        position = position.clone();
        meta = Map.copyOf(meta);
    }

    @Override
    public int[] position() {
        return position.clone();
    }
}
