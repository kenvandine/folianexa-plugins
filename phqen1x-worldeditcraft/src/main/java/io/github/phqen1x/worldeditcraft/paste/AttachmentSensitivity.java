package io.github.phqen1x.worldeditcraft.paste;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;

/**
 * Decides whether a block state needs its neighbours to already exist —
 * torches, buttons, levers, doors, beds, banners, rails, redstone, and
 * gravity-affected blocks, per the design doc's "The paste engine": pass
 * 1 places solids with physics suppressed, pass 2 places everything in
 * this category (plus every block entity, checked separately by {@link
 * PastePlan}, regardless of what this method says).
 *
 * <p>This is a keyword heuristic over the block id, not a lookup against
 * a real block-property registry (this codebase has no such registry —
 * see {@code BuildScriptValidator}'s class docs for the same documented
 * gap on the generation side). It covers the categories the design doc
 * names explicitly; it will miss edge cases outside them.
 */
final class AttachmentSensitivity {

    private static final String[] KEYWORDS = {
            "torch", "button", "lever", "door", "bed", "banner", "rail",
            "redstone", "repeater", "comparator", "trapdoor", "pressure_plate",
            "sign", "candle", "campfire", "vine", "ladder", "carpet",
            "tripwire", "chain", "hanging", "sand", "gravel", "anvil",
            "concrete_powder", "dripstone", "flower_pot", "cake",
    };

    private AttachmentSensitivity() {
    }

    static boolean isAttachmentSensitive(BlockStateRef state) {
        String id = state.id();
        for (String keyword : KEYWORDS) {
            if (id.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
