package io.github.kenvandine.hungergames;

import java.util.List;

/**
 * Layers an (optional) {@link TwistConfig} on top of the base {@code
 * game:} rules: every non-null field on the twist overrides the matching
 * base rule, and every null field leaves the base rule alone. Plain Java,
 * no org.bukkit.* dependency, so this is unit-testable without a server.
 */
final class RuleResolver {

    private RuleResolver() {
    }

    static EffectiveRules resolve(HungerGamesConfig.Game base, TwistConfig twist) {
        if (twist == null) {
            return new EffectiveRules(
                    base.gracePeriodSeconds(),
                    base.gameLengthSeconds(),
                    base.deathmatchCountdownSeconds(),
                    1.0,
                    base.finalBorderDiameter(),
                    base.deathmatchBorderDiameter(),
                    base.borderDamageAmount(),
                    base.borderDamageBuffer(),
                    base.borderWarningDistance(),
                    List.of()
            );
        }

        return new EffectiveRules(
                orElse(twist.gracePeriodSeconds(), base.gracePeriodSeconds()),
                orElse(twist.gameLengthSeconds(), base.gameLengthSeconds()),
                orElse(twist.deathmatchCountdownSeconds(), base.deathmatchCountdownSeconds()),
                twist.lootMultiplier() != null ? twist.lootMultiplier() : 1.0,
                orElse(twist.finalBorderDiameter(), base.finalBorderDiameter()),
                orElse(twist.deathmatchBorderDiameter(), base.deathmatchBorderDiameter()),
                base.borderDamageAmount(),
                base.borderDamageBuffer(),
                base.borderWarningDistance(),
                twist.potionEffects() == null ? List.of() : twist.potionEffects()
        );
    }

    private static int orElse(Integer override, int fallback) {
        return override != null ? override : fallback;
    }
}
