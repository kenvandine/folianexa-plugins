package io.github.phqen1x.worldeditcraft.paste;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentSensitivityTest {

    @Test
    void flagsEveryCategoryTheDesignDocNames() {
        String[] sensitive = {
                "minecraft:redstone_torch", "minecraft:oak_button", "minecraft:lever",
                "minecraft:oak_door", "minecraft:red_bed", "minecraft:white_banner",
                "minecraft:rail", "minecraft:redstone_wire", "minecraft:sand", "minecraft:gravel",
        };
        for (String id : sensitive) {
            assertTrue(AttachmentSensitivity.isAttachmentSensitive(BlockStateRef.parse(id)), id + " should be attachment-sensitive");
        }
    }

    @Test
    void ordinaryStructuralBlocksAreNotFlagged() {
        String[] ordinary = {
                "minecraft:stone", "minecraft:oak_planks", "minecraft:polished_blackstone_bricks",
                "minecraft:deepslate_tiles", "minecraft:glass",
        };
        for (String id : ordinary) {
            assertFalse(AttachmentSensitivity.isAttachmentSensitive(BlockStateRef.parse(id)), id + " should not be attachment-sensitive");
        }
    }
}
