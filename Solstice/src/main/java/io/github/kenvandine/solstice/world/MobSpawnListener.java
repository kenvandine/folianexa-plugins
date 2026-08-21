package io.github.kenvandine.solstice.world;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Seasonal mob replacements (PLAN.md §3.1): summer husks replace zombies, winter strays replace
 * skeletons, and a fraction of autumn spawns wear a carved pumpkin. Spawn-rate/frequency tuning
 * for the other per-season spawn behaviors (extra passive babies, jungle-everywhere, bee rate,
 * etc.) is out of scope for this build — it needs deeper spawn-injection than a single event
 * listener can give, and is left as a documented gap rather than a half implementation.
 */
public final class MobSpawnListener implements Listener {

    private final Solstice plugin;

    public MobSpawnListener(Solstice plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        WorldSeasonState state = plugin.calendarEngine().stateOf(event.getLocation().getWorld());
        if (state == null) {
            return;
        }
        var main = plugin.config().main();

        if (state.season() == Season.SUMMER && main.summerHusks() && event.getEntityType() == EntityType.ZOMBIE) {
            event.setCancelled(true);
            event.getLocation().getWorld().spawnEntity(event.getLocation(), EntityType.HUSK, event.getSpawnReason());
            return;
        }

        if (state.season() == Season.WINTER && main.winterStrays() && event.getEntityType() == EntityType.SKELETON) {
            event.setCancelled(true);
            event.getLocation().getWorld().spawnEntity(event.getLocation(), EntityType.STRAY, event.getSpawnReason());
            return;
        }

        if (state.season() == Season.AUTUMN && isPumpkinEligible(event.getEntityType())
                && ThreadLocalRandom.current().nextDouble() < main.autumnPumpkinChance()) {
            if (event.getEntity() instanceof Mob mob && mob instanceof LivingEntity living) {
                EntityEquipment equipment = living.getEquipment();
                if (equipment != null) {
                    equipment.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                    equipment.setHelmetDropChance(0f);
                }
            }
        }
    }

    private boolean isPumpkinEligible(EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, HUSK, STRAY, SPIDER, CREEPER, ENDERMAN -> true;
            default -> false;
        };
    }
}
