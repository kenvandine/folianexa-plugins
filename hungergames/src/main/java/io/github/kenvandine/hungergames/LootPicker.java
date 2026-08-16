package io.github.kenvandine.hungergames;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weighted-random loot selection for stocking arena chests. Plain Java,
 * seeded-{@link Random}-friendly, so this is unit-testable without a
 * server — actual {@code ItemStack} creation happens in GameManager, the
 * only class that touches game state (same split as CampusScene/
 * SceneBuilder in the campus-lobby plugin).
 */
final class LootPicker {

    private LootPicker() {
    }

    /** Picks {@code count} loot entries (with repeats) from {@code pool}, weighted by {@link LootEntry#weight()}. */
    static List<LootEntry> pickChestContents(List<LootEntry> pool, int count, Random random) {
        if (pool.isEmpty() || count <= 0) {
            return List.of();
        }
        double totalWeight = pool.stream().mapToDouble(LootEntry::weight).sum();
        if (totalWeight <= 0) {
            return List.of();
        }

        List<LootEntry> picked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double roll = random.nextDouble() * totalWeight;
            double cumulative = 0;
            for (LootEntry entry : pool) {
                cumulative += entry.weight();
                if (roll < cumulative) {
                    picked.add(entry);
                    break;
                }
            }
        }
        return picked;
    }

    /** Resolves a concrete stack amount within {@code entry}'s [min, max] range. */
    static int resolveAmount(LootEntry entry, Random random) {
        if (entry.maxAmount() <= entry.minAmount()) {
            return entry.minAmount();
        }
        return entry.minAmount() + random.nextInt(entry.maxAmount() - entry.minAmount() + 1);
    }
}
