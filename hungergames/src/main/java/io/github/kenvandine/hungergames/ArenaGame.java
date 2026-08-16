package io.github.kenvandine.hungergames;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The state machine for a single arena's match: who has queued, who is
 * still alive, kill credit, and the {@link GameState} transitions between
 * them. This holds player identity as bare {@link UUID}s rather than live
 * {@code Player} references (same convention as StatsTracker in the
 * folianexa-stats plugin) and has no other org.bukkit.* dependency, so it's
 * unit-testable without a server. GameManager is the only class that acts
 * on these transitions with real game state (teleports, the world border,
 * chest loot, potion effects).
 */
final class ArenaGame {

    private final ArenaConfig arena;
    private final Set<UUID> tributes = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, Integer> kills = new LinkedHashMap<>();

    private GameState state = GameState.WAITING;
    private TwistConfig twist;
    private EffectiveRules rules;
    private Instant countdownEndsAt;
    private Instant gracePeriodEndsAt;
    private Instant deathmatchTriggersAt;
    private UUID winner;

    ArenaGame(ArenaConfig arena) {
        this.arena = arena;
    }

    ArenaConfig arena() {
        return arena;
    }

    GameState state() {
        return state;
    }

    Set<UUID> tributes() {
        return Collections.unmodifiableSet(tributes);
    }

    Set<UUID> alive() {
        return Collections.unmodifiableSet(alive);
    }

    Map<UUID, Integer> kills() {
        return Collections.unmodifiableMap(kills);
    }

    Optional<UUID> winner() {
        return Optional.ofNullable(winner);
    }

    Optional<TwistConfig> activeTwist() {
        return Optional.ofNullable(twist);
    }

    EffectiveRules rules() {
        return rules;
    }

    boolean isTribute(UUID id) {
        return tributes.contains(id);
    }

    boolean isAlive(UUID id) {
        return alive.contains(id);
    }

    boolean isJoinable() {
        return state == GameState.WAITING || state == GameState.COUNTDOWN;
    }

    boolean addTribute(UUID id, int capacity) {
        if (!isJoinable() || tributes.contains(id) || tributes.size() >= capacity) {
            return false;
        }
        tributes.add(id);
        return true;
    }

    boolean removeTribute(UUID id) {
        return isJoinable() && tributes.remove(id);
    }

    void startCountdown(Instant now, int countdownSeconds) {
        state = GameState.COUNTDOWN;
        countdownEndsAt = now.plusSeconds(countdownSeconds);
    }

    void cancelCountdown() {
        state = GameState.WAITING;
        countdownEndsAt = null;
    }

    boolean countdownElapsed(Instant now) {
        return state == GameState.COUNTDOWN && countdownEndsAt != null && !now.isBefore(countdownEndsAt);
    }

    /** Starts a match: locks in the tribute roster as the alive set, and resolves the effective rules for the chosen twist (if any). */
    void beginGracePeriod(Instant now, TwistConfig chosenTwist, HungerGamesConfig.Game baseRules) {
        this.twist = chosenTwist;
        this.rules = RuleResolver.resolve(baseRules, chosenTwist);
        alive.clear();
        alive.addAll(tributes);
        kills.clear();
        winner = null;
        state = GameState.GRACE_PERIOD;
        gracePeriodEndsAt = now.plusSeconds(rules.gracePeriodSeconds());
    }

    boolean gracePeriodElapsed(Instant now) {
        return state == GameState.GRACE_PERIOD && gracePeriodEndsAt != null && !now.isBefore(gracePeriodEndsAt);
    }

    void beginActive(Instant now) {
        state = GameState.ACTIVE;
        deathmatchTriggersAt = now.plusSeconds(rules.gameLengthSeconds());
    }

    boolean gameLengthElapsed(Instant now) {
        return state == GameState.ACTIVE && deathmatchTriggersAt != null && !now.isBefore(deathmatchTriggersAt) && alive.size() > 1;
    }

    void beginDeathmatch() {
        state = GameState.DEATHMATCH;
    }

    void recordKill(UUID killer) {
        kills.merge(killer, 1, Integer::sum);
    }

    /**
     * Eliminates a tribute. Returns true if this ended the match (one or
     * zero tributes remain); the caller is responsible for reacting to
     * that (announcing the winner, returning everyone to the lobby).
     */
    boolean eliminate(UUID id) {
        if (state != GameState.ACTIVE && state != GameState.DEATHMATCH) {
            return false;
        }
        if (!alive.remove(id)) {
            return false;
        }
        if (alive.size() <= 1) {
            state = GameState.ENDING;
            winner = alive.isEmpty() ? null : alive.iterator().next();
            return true;
        }
        return false;
    }

    /** Clears this arena's match back to a fresh waiting queue. */
    void reset() {
        state = GameState.WAITING;
        tributes.clear();
        alive.clear();
        kills.clear();
        winner = null;
        twist = null;
        rules = null;
        countdownEndsAt = null;
        gracePeriodEndsAt = null;
        deathmatchTriggersAt = null;
    }
}
