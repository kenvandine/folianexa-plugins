package io.github.kenvandine.solstice.temperature;

import io.github.kenvandine.solstice.api.event.SeasonParticleStartEvent;
import io.github.kenvandine.solstice.config.Messages;
import io.github.kenvandine.solstice.config.TemperatureConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies the gameplay effects described in temperature.yml's `effects` section (PLAN.md §3.4)
 * for one player, given that recalculation's air/apparent temperatures. Must run on the thread
 * that owns the player entity (the caller — {@link TemperatureManager} — already does, via the
 * entity scheduler).
 */
final class TemperatureEffects {

    private TemperatureEffects() {
    }

    // Refresh potion effects for slightly longer than the recalculation interval so they don't
    // flicker off between recalculations, without needing a duration exactly synced to the loop.
    private static final int EFFECT_DURATION_TICKS = 80;

    static void apply(Player player, TemperatureConfig cfg, Messages messages,
                       double airTemperature, double apparentTemperature, EffectRuntimeState state) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        String warningKey = null;

        if (apparentTemperature <= cfg.freezingDamageBelowC) {
            long now = System.currentTimeMillis();
            long intervalMillis = cfg.freezingDamageIntervalSeconds * 1000L;
            if (now - state.lastFreezeDamageMillis >= intervalMillis) {
                state.lastFreezeDamageMillis = now;
                player.damage(1.0);
            }
            warningKey = "freezing";
        }

        if (apparentTemperature <= cfg.slownessBelowC || apparentTemperature >= cfg.slownessAboveC) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_DURATION_TICKS, 0, true, false, true));
        }

        if (apparentTemperature <= cfg.hungerBelowC) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, EFFECT_DURATION_TICKS, 0, true, false, true));
        }

        if (cfg.comfortableEffect != null && apparentTemperature >= cfg.comfortableMinC && apparentTemperature <= cfg.comfortableMaxC) {
            player.addPotionEffect(new PotionEffect(cfg.comfortableEffect, EFFECT_DURATION_TICKS, cfg.comfortableEffectAmplifier, true, false, false));
        }

        if (apparentTemperature >= cfg.burningAboveC) {
            player.setFireTicks(Math.max(player.getFireTicks(), 60));
            warningKey = "burning";
        } else if (apparentTemperature >= cfg.sweatingAboveC) {
            spawnParticle(player, "sweat", Particle.SPLASH, eyeMinusOffset(player));
            if (warningKey == null) {
                warningKey = "hot";
            }
        }

        if (airTemperature <= cfg.coldBreathAtOrBelowC) {
            spawnParticle(player, "cold_breath", Particle.CLOUD, mouthLocation(player));
        }

        sendWarningIfChanged(player, messages, state, warningKey);
    }

    private static void sendWarningIfChanged(Player player, Messages messages, EffectRuntimeState state, String warningKey) {
        String key = warningKey == null ? "" : warningKey;
        if (key.equals(state.lastWarningKey)) {
            return;
        }
        state.lastWarningKey = key;
        if (!key.isEmpty() && messages != null) {
            String messageKey = "temperature.warning-" + key;
            player.sendMessage(messages.get(messageKey));
        }
    }

    private static void spawnParticle(Player player, String kind, Particle particle, Location at) {
        SeasonParticleStartEvent event = new SeasonParticleStartEvent(player, at, kind);
        player.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            player.getWorld().spawnParticle(particle, at, 3, 0.2, 0.2, 0.2, 0.0);
        }
    }

    private static Location mouthLocation(Player player) {
        return player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(0.3));
    }

    private static Location eyeMinusOffset(Player player) {
        return player.getEyeLocation();
    }
}
