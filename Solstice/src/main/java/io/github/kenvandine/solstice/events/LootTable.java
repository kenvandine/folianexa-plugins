package io.github.kenvandine.solstice.events;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parses the `min-max:ITEM_NAME` (or bare `amount:ITEM_NAME`) notation used by events.yml's
 * gift-loot lists (PLAN.md §3.6). Each configured line is one guaranteed item stack with a
 * randomized amount in its range — not a single weighted pick among the lines.
 */
public final class LootTable {

    private record Entry(int min, int max, Material material) {
    }

    private final List<Entry> entries;

    private LootTable(List<Entry> entries) {
        this.entries = entries;
    }

    public static LootTable parse(List<String> lines) {
        List<Entry> entries = new ArrayList<>();
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String amountPart = line.substring(0, colon).trim();
            String materialPart = line.substring(colon + 1).trim();
            Material material;
            try {
                material = Material.valueOf(materialPart.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;
            }

            int min, max;
            int dash = amountPart.indexOf('-');
            try {
                if (dash > 0) {
                    min = Integer.parseInt(amountPart.substring(0, dash).trim());
                    max = Integer.parseInt(amountPart.substring(dash + 1).trim());
                } else {
                    min = max = Integer.parseInt(amountPart);
                }
            } catch (NumberFormatException e) {
                continue;
            }
            entries.add(new Entry(Math.min(min, max), Math.max(min, max), material));
        }
        return new LootTable(entries);
    }

    public List<ItemStack> roll() {
        List<ItemStack> result = new ArrayList<>(entries.size());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Entry entry : entries) {
            int amount = entry.min() == entry.max() ? entry.min() : random.nextInt(entry.min(), entry.max() + 1);
            if (amount > 0) {
                result.add(new ItemStack(entry.material(), amount));
            }
        }
        return result;
    }
}
