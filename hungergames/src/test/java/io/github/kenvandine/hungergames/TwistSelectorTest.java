package io.github.kenvandine.hungergames;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwistSelectorTest {

    private static TwistConfig twist(String id, double weight) {
        return new TwistConfig(id, id, "desc", weight, null, null, null, null, null, null, null);
    }

    @Test
    void disabledTwistsNeverSelectAnything() {
        HungerGamesConfig.Twists twists = new HungerGamesConfig.Twists(false, 1.0, List.of(twist("a", 1)));

        assertTrue(TwistSelector.select(twists, new Random(1)).isEmpty());
    }

    @Test
    void emptyPoolNeverSelectsAnything() {
        HungerGamesConfig.Twists twists = new HungerGamesConfig.Twists(true, 1.0, List.of());

        assertTrue(TwistSelector.select(twists, new Random(1)).isEmpty());
    }

    @Test
    void zeroChanceNeverSelectsAnything() {
        HungerGamesConfig.Twists twists = new HungerGamesConfig.Twists(true, 0.0, List.of(twist("a", 1)));

        for (long seed = 0; seed < 20; seed++) {
            assertTrue(TwistSelector.select(twists, new Random(seed)).isEmpty());
        }
    }

    @Test
    void fullChanceWithOneEntryAlwaysSelectsIt() {
        HungerGamesConfig.Twists twists = new HungerGamesConfig.Twists(true, 1.0, List.of(twist("only", 1)));

        for (long seed = 0; seed < 20; seed++) {
            Optional<TwistConfig> picked = TwistSelector.select(twists, new Random(seed));
            assertTrue(picked.isPresent());
            assertEquals("only", picked.get().id());
        }
    }

    @Test
    void weightedPickOnlyEverReturnsPoolMembers() {
        HungerGamesConfig.Twists twists = new HungerGamesConfig.Twists(true, 1.0,
                List.of(twist("common", 10), twist("rare", 1)));

        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            Optional<TwistConfig> picked = TwistSelector.select(twists, random);
            assertTrue(picked.isPresent());
            assertTrue(picked.get().id().equals("common") || picked.get().id().equals("rare"));
        }
    }
}
