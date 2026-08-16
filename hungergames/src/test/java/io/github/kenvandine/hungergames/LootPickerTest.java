package io.github.kenvandine.hungergames;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootPickerTest {

    @Test
    void emptyPoolPicksNothing() {
        assertTrue(LootPicker.pickChestContents(List.of(), 5, new Random(1)).isEmpty());
    }

    @Test
    void zeroOrNegativeCountPicksNothing() {
        List<LootEntry> pool = List.of(new LootEntry("STONE", 1, 1, 1));
        assertTrue(LootPicker.pickChestContents(pool, 0, new Random(1)).isEmpty());
        assertTrue(LootPicker.pickChestContents(pool, -3, new Random(1)).isEmpty());
    }

    @Test
    void picksExactlyTheRequestedCount() {
        List<LootEntry> pool = List.of(
                new LootEntry("STONE", 1, 1, 1),
                new LootEntry("IRON_INGOT", 1, 1, 1)
        );
        assertEquals(7, LootPicker.pickChestContents(pool, 7, new Random(5)).size());
    }

    @Test
    void onlyEverPicksFromThePool() {
        List<LootEntry> pool = List.of(
                new LootEntry("A", 1, 1, 3),
                new LootEntry("B", 1, 1, 1)
        );
        Set<String> allowed = Set.of("A", "B");
        Random random = new Random(99);
        for (LootEntry pick : LootPicker.pickChestContents(pool, 100, random)) {
            assertTrue(allowed.contains(pick.material()));
        }
    }

    @Test
    void resolveAmountStaysWithinMinMaxRange() {
        LootEntry entry = new LootEntry("ARROW", 4, 12, 1);
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            int amount = LootPicker.resolveAmount(entry, random);
            assertTrue(amount >= 4 && amount <= 12, "amount " + amount + " out of range");
        }
    }

    @Test
    void resolveAmountIsExactWhenMinEqualsMax() {
        LootEntry entry = new LootEntry("GOLDEN_APPLE", 1, 1, 1);
        assertEquals(1, LootPicker.resolveAmount(entry, new Random(3)));
    }
}
