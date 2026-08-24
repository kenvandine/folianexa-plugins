package io.github.kenvandine.flowerwatch;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerMaterialsTest {

    @Test
    void recognizesShortAndTallFlowers() {
        assertTrue(FlowerMaterials.isFlower(Material.POPPY));
        assertTrue(FlowerMaterials.isFlower(Material.TORCHFLOWER));
        assertTrue(FlowerMaterials.isFlower(Material.SUNFLOWER));
        assertTrue(FlowerMaterials.isFlower(Material.PEONY));
    }

    @Test
    void rejectsNonFlowerBlocksAndNull() {
        assertFalse(FlowerMaterials.isFlower(Material.DIRT));
        assertFalse(FlowerMaterials.isFlower(Material.GRASS_BLOCK));
        assertFalse(FlowerMaterials.isFlower(Material.POTTED_POPPY));
        assertFalse(FlowerMaterials.isFlower(null));
    }
}
