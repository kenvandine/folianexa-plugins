package io.github.kenvandine.hungergames;

import java.util.List;

/** The base {@link HungerGamesConfig.Game} rules with a twist (if any) layered on — see {@link RuleResolver}. */
record EffectiveRules(
        int gracePeriodSeconds,
        int gameLengthSeconds,
        int deathmatchCountdownSeconds,
        double lootMultiplier,
        int finalBorderDiameter,
        int deathmatchBorderDiameter,
        double borderDamageAmount,
        double borderDamageBuffer,
        int borderWarningDistance,
        List<PotionEffectSpec> potionEffects
) {
}
