package io.github.kenvandine.flowerwatch.scan;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pure bookkeeping for {@link DensityScanner}: remembers each chunk's
 * flower count from its previous scan and reports how much it grew by.
 * No Bukkit types here, so this is plain-JUnit testable independent of
 * any server/scheduler setup.
 */
public final class DensityTracker {

    private final Map<ChunkKey, Integer> lastCount = new HashMap<>();

    /**
     * Records {@code newCount} for {@code key} and returns the increase
     * since the last recorded count for that chunk, if any. A chunk's
     * first scan never reports a delta (there's nothing to compare
     * against yet) even if it already has a lot of flowers — that's
     * pre-existing world state, not a burst.
     */
    public Optional<Integer> recordAndDelta(ChunkKey key, int newCount) {
        Integer previous = lastCount.put(key, newCount);
        if (previous == null) {
            return Optional.empty();
        }
        int delta = newCount - previous;
        return delta > 0 ? Optional.of(delta) : Optional.empty();
    }

    public int size() {
        return lastCount.size();
    }
}
