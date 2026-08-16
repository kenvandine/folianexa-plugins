package io.github.kenvandine.hungergames;

/**
 * A potion effect a twist grants every tribute at match start.
 * {@code type} is a raw {@code org.bukkit.potion.PotionEffectType} name kept
 * as a plain String so this class carries no org.bukkit.* dependency.
 */
record PotionEffectSpec(String type, int amplifier, int durationSeconds) {
}
