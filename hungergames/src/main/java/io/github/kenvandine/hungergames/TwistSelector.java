package io.github.kenvandine.hungergames;

import java.util.Optional;
import java.util.Random;

/**
 * Picks a twist for a new match: first an overall roll against {@code
 * twists.chance} decides whether any twist applies at all, then a
 * weighted pick chooses which one from {@code twists.pool}. Plain Java,
 * seeded-{@link Random}-friendly, so this is unit-testable without a
 * server.
 */
final class TwistSelector {

    private TwistSelector() {
    }

    static Optional<TwistConfig> select(HungerGamesConfig.Twists twists, Random random) {
        if (!twists.enabled() || twists.pool().isEmpty()) {
            return Optional.empty();
        }
        if (random.nextDouble() >= twists.chance()) {
            return Optional.empty();
        }

        double totalWeight = twists.pool().stream().mapToDouble(TwistConfig::weight).sum();
        if (totalWeight <= 0) {
            return Optional.empty();
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (TwistConfig twist : twists.pool()) {
            cumulative += twist.weight();
            if (roll < cumulative) {
                return Optional.of(twist);
            }
        }
        // Floating-point rounding can leave a hair of weight unaccounted
        // for — fall back to the last entry rather than returning empty.
        return Optional.of(twists.pool().get(twists.pool().size() - 1));
    }
}
