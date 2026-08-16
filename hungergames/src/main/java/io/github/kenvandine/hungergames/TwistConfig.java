package io.github.kenvandine.hungergames;

import java.util.List;

/**
 * A single configurable "twist" (Quarter-Quell-style rule change) that can
 * be picked for a match. Every rule field is a nullable override: {@code
 * null} means "leave the base {@link HungerGamesConfig.Game} rule alone."
 * See {@link RuleResolver} for how a twist is layered onto the base rules,
 * and {@link TwistSelector} for how one is picked.
 *
 * This is entirely config-driven on purpose — see config.yml's {@code
 * twists.pool} section — so operators can add a brand-new twist (or tune an
 * existing one) purely by editing config.yml, no code changes needed.
 */
record TwistConfig(
        String id,
        String displayName,
        String description,
        double weight,
        Integer gracePeriodSeconds,
        Double lootMultiplier,
        Integer gameLengthSeconds,
        Integer deathmatchCountdownSeconds,
        Integer finalBorderDiameter,
        Integer deathmatchBorderDiameter,
        List<PotionEffectSpec> potionEffects
) {
}
