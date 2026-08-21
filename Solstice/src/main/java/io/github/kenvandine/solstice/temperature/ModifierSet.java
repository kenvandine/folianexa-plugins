package io.github.kenvandine.solstice.temperature;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Tracks player-scoped timed and permanent temperature modifiers (SolsticeAPI's
 * applyTimedTemperatureEffect / applyPermanentTemperatureEffect, PLAN.md §3.9). One
 * {@code CopyOnWriteArrayList} per player: writes (apply/cancel/expire) are rare, reads (summing
 * each recalculation) are frequent.
 */
public final class ModifierSet {

    public record Effect(UUID id, double delta, long expiresAtMillis) {
        public boolean isPermanent() {
            return expiresAtMillis < 0;
        }

        public boolean isExpired(long nowMillis) {
            return !isPermanent() && nowMillis >= expiresAtMillis;
        }
    }

    private final Map<UUID, CopyOnWriteArrayList<Effect>> byPlayer = new ConcurrentHashMap<>();

    public UUID addTimed(UUID playerId, double delta, int seconds) {
        UUID id = UUID.randomUUID();
        list(playerId).add(new Effect(id, delta, System.currentTimeMillis() + seconds * 1000L));
        return id;
    }

    public UUID addPermanent(UUID playerId, double delta) {
        UUID id = UUID.randomUUID();
        list(playerId).add(new Effect(id, delta, -1));
        return id;
    }

    public void cancel(UUID playerId, UUID effectId) {
        CopyOnWriteArrayList<Effect> list = byPlayer.get(playerId);
        if (list != null) {
            list.removeIf(e -> e.id().equals(effectId));
        }
    }

    public void clear(UUID playerId) {
        CopyOnWriteArrayList<Effect> list = byPlayer.get(playerId);
        if (list != null) {
            list.clear();
        }
    }

    /** Sums non-expired effects for a player, pruning expired ones as a side effect. */
    public double sumActive(UUID playerId) {
        CopyOnWriteArrayList<Effect> list = byPlayer.get(playerId);
        if (list == null || list.isEmpty()) {
            return 0.0;
        }
        long now = System.currentTimeMillis();
        list.removeIf(e -> e.isExpired(now));
        double total = 0.0;
        for (Effect e : list) {
            total += e.delta();
        }
        return total;
    }

    private CopyOnWriteArrayList<Effect> list(UUID playerId) {
        return byPlayer.computeIfAbsent(playerId, id -> new CopyOnWriteArrayList<>());
    }

    public void forget(UUID playerId) {
        byPlayer.remove(playerId);
    }
}
