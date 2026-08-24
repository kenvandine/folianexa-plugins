package io.github.kenvandine.flowerwatch;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Every vanilla block this plugin treats as "a flower" for logging and
 * density-scanning purposes. Potted variants (POTTED_*) are deliberately
 * excluded — a potted flower can't spread/grow/spawn on its own the way
 * a ground flower can, so it isn't relevant to a runaway
 * flower-generation bug.
 *
 * There is no vanilla "wildflowers" block in the 1.21.4 API this plugin
 * targets (it doesn't exist as a real {@link Material} constant) — left
 * out rather than guessed at.
 */
public final class FlowerMaterials {

    /** Single-block ("short") flowers. */
    public static final Set<Material> SHORT = EnumSet.of(
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.TORCHFLOWER,
            Material.WITHER_ROSE,
            Material.PINK_PETALS,
            Material.OPEN_EYEBLOSSOM,
            Material.CLOSED_EYEBLOSSOM
    );

    /** Both halves (bottom and top) of two-block-tall flowers. */
    public static final Set<Material> TALL = EnumSet.of(
            Material.SUNFLOWER,
            Material.LILAC,
            Material.ROSE_BUSH,
            Material.PEONY
    );

    public static final Set<Material> ALL;

    static {
        EnumSet<Material> all = EnumSet.noneOf(Material.class);
        all.addAll(SHORT);
        all.addAll(TALL);
        ALL = EnumSet.copyOf(all);
    }

    public static boolean isFlower(Material material) {
        return material != null && ALL.contains(material);
    }

    private FlowerMaterials() {
    }
}
