package io.github.kenvandine.solstice.temperature;

import io.github.kenvandine.solstice.config.TemperatureConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Per-material (optionally CustomModelData-narrowed) temperature modifiers for held or worn items
 * (PLAN.md §3.4 "Custom temperature items").
 */
public final class CustomTempItems {

    private CustomTempItems() {
    }

    public static double modifierFor(TemperatureConfig cfg, Player player) {
        if (cfg.customItems.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        ItemStack held = player.getInventory().getItemInMainHand();
        for (TemperatureConfig.CustomItem item : cfg.customItems) {
            if (!item.armorSlot() && matches(held, item)) {
                total += item.modifier();
            }
        }
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            for (TemperatureConfig.CustomItem item : cfg.customItems) {
                if (item.armorSlot() && matches(armorPiece, item)) {
                    total += item.modifier();
                }
            }
        }
        return total;
    }

    private static boolean matches(ItemStack stack, TemperatureConfig.CustomItem item) {
        if (stack == null || stack.getType() != item.material()) {
            return false;
        }
        if (item.customModelData() == null) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == item.customModelData();
    }
}
