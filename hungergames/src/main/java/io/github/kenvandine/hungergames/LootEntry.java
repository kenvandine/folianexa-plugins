package io.github.kenvandine.hungergames;

/**
 * One weighted entry in the loot table used to stock arena chests.
 * {@code material} is a raw {@code org.bukkit.Material} enum name kept as a
 * plain String so this class carries no org.bukkit.* dependency — see
 * BlockPlacement in the campus-lobby plugin for the same convention.
 */
record LootEntry(String material, int minAmount, int maxAmount, double weight) {
}
