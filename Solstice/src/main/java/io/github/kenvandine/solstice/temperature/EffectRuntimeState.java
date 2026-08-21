package io.github.kenvandine.solstice.temperature;

/**
 * Per-player mutable bookkeeping for temperature effects (damage-tick pacing, warning-message
 * de-duplication). Only ever touched by that player's own entity-scheduler thread, so plain
 * fields are safe — no volatile/synchronized needed (PLAN.md §2's single-writer-per-key pattern).
 */
final class EffectRuntimeState {
    long lastFreezeDamageMillis;
    String lastWarningKey = "";
}
