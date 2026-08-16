package io.github.kenvandine.hungergames;

/**
 * An arena's match lifecycle. Transitions in order: {@code WAITING} ->
 * {@code COUNTDOWN} (cancels back to {@code WAITING} if tributes drop below
 * the minimum) -> {@code GRACE_PERIOD} -> {@code ACTIVE} ->
 * {@code DEATHMATCH} (only if more than one tribute survives to the end of
 * the game-length timer) -> {@code ENDING} -> back to {@code WAITING}.
 */
enum GameState {
    WAITING,
    COUNTDOWN,
    GRACE_PERIOD,
    ACTIVE,
    DEATHMATCH,
    ENDING
}
