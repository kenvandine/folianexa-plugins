package io.github.kenvandine.hungergames;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleResolverTest {

    private final HungerGamesConfig.Game base = HungerGamesConfig.defaults().game();

    @Test
    void noTwistLeavesBaseRulesUntouched() {
        EffectiveRules rules = RuleResolver.resolve(base, null);

        assertEquals(base.gracePeriodSeconds(), rules.gracePeriodSeconds());
        assertEquals(base.gameLengthSeconds(), rules.gameLengthSeconds());
        assertEquals(base.deathmatchCountdownSeconds(), rules.deathmatchCountdownSeconds());
        assertEquals(base.finalBorderDiameter(), rules.finalBorderDiameter());
        assertEquals(base.deathmatchBorderDiameter(), rules.deathmatchBorderDiameter());
        assertEquals(1.0, rules.lootMultiplier());
        assertTrue(rules.potionEffects().isEmpty());
    }

    @Test
    void twistOverridesOnlyItsSetFields() {
        TwistConfig bloodbath = new TwistConfig("bloodbath", "Bloodbath", "desc", 1.0,
                0, null, null, null, null, null, null);

        EffectiveRules rules = RuleResolver.resolve(base, bloodbath);

        assertEquals(0, rules.gracePeriodSeconds());
        // Everything else falls back to the base config unchanged.
        assertEquals(base.gameLengthSeconds(), rules.gameLengthSeconds());
        assertEquals(base.finalBorderDiameter(), rules.finalBorderDiameter());
        assertEquals(1.0, rules.lootMultiplier());
    }

    @Test
    void twistCanOverrideEveryField() {
        List<PotionEffectSpec> effects = List.of(new PotionEffectSpec("SPEED", 1, 30));
        TwistConfig quell = new TwistConfig("quell", "Quell", "desc", 1.0,
                5, 2.0, 480, 10, 12, 4, effects);

        EffectiveRules rules = RuleResolver.resolve(base, quell);

        assertEquals(5, rules.gracePeriodSeconds());
        assertEquals(2.0, rules.lootMultiplier());
        assertEquals(480, rules.gameLengthSeconds());
        assertEquals(10, rules.deathmatchCountdownSeconds());
        assertEquals(12, rules.finalBorderDiameter());
        assertEquals(4, rules.deathmatchBorderDiameter());
        assertEquals(effects, rules.potionEffects());
    }
}
