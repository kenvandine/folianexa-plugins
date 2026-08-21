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

    // Refresh potion effects for several recalculation intervals so they don't flicker off
    // between recalculations, without needing a duration exactly synced to the loop.
    private static final int EFFECT_DURATION_TICKS = 200;

    static void apply(Player player, TemperatureConfig cfg, Messages messages,
                       double airTemperature, double apparentTemperature, EffectRuntimeState state) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Effects are only reapplied once they're close to expiring (see refreshIfNeeded), not
        // on every recalculation: re-adding an already-active PotionEffect forces the client to
        // briefly remove and reinstate the underlying attribute modifier (e.g. movement speed
        // for Slowness), which reads as a stutter every time it happens.
        int periodTicks = Math.max(1, cfg.recalculateIntervalSeconds * 20);

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
            refreshIfNeeded(player, PotionEffectType.SLOWNESS, 0, periodTicks, true);
        }

        if (apparentTemperature <= cfg.hungerBelowC) {
            refreshIfNeeded(player, PotionEffectType.HUNGER, 0, periodTicks, true);
        }

        if (cfg.comfortableEffect != null && apparentTemperature >= cfg.comfortableMinC && apparentTemperature <= cfg.comfortableMaxC) {
            refreshIfNeeded(player, cfg.comfortableEffect, cfg.comfortableEffectAmplifier, periodTicks, false);
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

    // Only calls addPotionEffect when the effect is missing, at a different amplifier, or close
    // enough to expiring that it might lapse before the next recalculation checks again.
    private static void refreshIfNeeded(Player player, PotionEffectType type, int amplifier, int periodTicks,
                                         boolean showIcon) {
        PotionEffect existing = player.getPotionEffect(type);
        if (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > periodTicks) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, EFFECT_DURATION_TICKS, amplifier, true, false, showIcon));
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
