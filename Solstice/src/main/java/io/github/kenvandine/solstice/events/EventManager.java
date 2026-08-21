package io.github.kenvandine.solstice.events;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.SeasonDate;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.api.event.DayChangeEvent;
import io.github.kenvandine.solstice.config.CalendarConfig;
import io.github.kenvandine.solstice.config.CustomEventsConfig;
import io.github.kenvandine.solstice.config.EventsConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives events.yml's four built-in events and custom-events.yml's dated/weekly/daily events
 * (PLAN.md §3.6). The generic contract — enabled, name, disabled-worlds, start/stop commands — is
 * fully implemented for all four built-ins. Flavor mechanics beyond that (procedural Christmas
 * trees, New Year village fireworks, Easter hidden eggs/killer bunnies) are out of scope for this
 * build; Christmas gift-loot and Halloween mob buffs are implemented since they're concrete and
 * self-contained. See the README for the full list of documented gaps.
 */
public final class EventManager implements Listener {

    private final Solstice plugin;
    private final Map<UUID, Set<String>> activeBuiltins = new ConcurrentHashMap<>();

    public EventManager(Solstice plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.calendarEngine().isManaged(world)) {
                continue;
            }
            WorldSeasonState state = plugin.calendarEngine().stateOf(world);
            activeBuiltins.put(world.getUID(), currentlyActive(state.date()));
        }
    }

    /** Display names of currently active, display-enabled built-in events for a world. */
    public List<String> activeEventNames(World world) {
        Set<String> active = activeBuiltins.getOrDefault(world.getUID(), Set.of());
        List<String> names = new java.util.ArrayList<>();
        for (EventsConfig.BuiltinEvent builtin : plugin.config().events().events()) {
            if (active.contains(builtin.key) && builtin.displayEvent) {
                names.add(ChatColor.translateAlternateColorCodes('&', builtin.name));
            }
        }
        return names;
    }

    @EventHandler
    public void onDayChange(DayChangeEvent event) {
        World world = event.getWorld();
        if (!plugin.calendarEngine().isManaged(world)) {
            return;
        }
        processBuiltinEvents(world, event.getTo());
        processCustomEvents(world, event.getTo());
    }

    private Set<String> currentlyActive(SeasonDate date) {
        Set<String> active = new HashSet<>();
        for (EventsConfig.BuiltinEvent builtin : plugin.config().events().events()) {
            if (builtin.enabled && builtin.isWithin(date.month(), date.day())) {
                active.add(builtin.key);
            }
        }
        return active;
    }

    private void processBuiltinEvents(World world, SeasonDate date) {
        Set<String> previous = activeBuiltins.getOrDefault(world.getUID(), Set.of());
        Set<String> current = currentlyActive(date);
        activeBuiltins.put(world.getUID(), current);

        for (EventsConfig.BuiltinEvent builtin : plugin.config().events().events()) {
            if (builtin.disabledWorlds.contains(world.getName())) {
                continue;
            }
            boolean wasActive = previous.contains(builtin.key);
            boolean isActive = current.contains(builtin.key);
            if (!wasActive && isActive) {
                runCommands(world, date, builtin.startCommands);
                onEventStart(builtin, world);
            } else if (wasActive && !isActive) {
                runCommands(world, date, builtin.stopCommands);
            }
        }
    }

    private void onEventStart(EventsConfig.BuiltinEvent builtin, World world) {
        if ("christmas".equals(builtin.key) && builtin.raw.getBoolean("particles", true)) {
            giveChristmasGifts(builtin, world);
        }
    }

    private void giveChristmasGifts(EventsConfig.BuiltinEvent builtin, World world) {
        LootTable table = LootTable.parse(builtin.raw.getStringList("gift-loot"));
        for (Player player : world.getPlayers()) {
            List<ItemStack> gifts = table.roll();
            if (gifts.isEmpty()) {
                continue;
            }
            plugin.schedulers().entity(player, () -> {
                for (ItemStack gift : gifts) {
                    player.getInventory().addItem(gift);
                }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aYou received a Christmas gift!"));
            }, () -> {
            });
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHalloweenSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (!activeBuiltins.getOrDefault(world.getUID(), Set.of()).contains("halloween")) {
            return;
        }
        EventsConfig.BuiltinEvent halloween = findBuiltin("halloween");
        if (halloween == null || !halloween.raw.getBoolean("mob-potion-buffs", true)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (event.getEntityType() == EntityType.WITCH && halloween.raw.getBoolean("witch-blindness-wither", true)) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
            living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0));
        } else if (isHostile(event.getEntityType())) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 6000, 0, true, false));
            living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 0, true, false));
        }
    }

    private boolean isHostile(EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, HUSK, STRAY, SPIDER, CREEPER, ENDERMAN, CAVE_SPIDER -> true;
            default -> false;
        };
    }

    private EventsConfig.BuiltinEvent findBuiltin(String key) {
        for (EventsConfig.BuiltinEvent builtin : plugin.config().events().events()) {
            if (builtin.key.equals(key)) {
                return builtin;
            }
        }
        return null;
    }

    private void processCustomEvents(World world, SeasonDate date) {
        CalendarConfig calendar = plugin.config().calendar();
        CustomEventsConfig custom = plugin.config().customEvents();

        for (CustomEventsConfig.DailyEvent daily : custom.daily()) {
            runCommands(world, date, daily.actions());
        }

        String weekdayName = calendar.weekdays().get(date.weekdayIndex());
        for (CustomEventsConfig.WeeklyEvent weekly : custom.weekly()) {
            if (weekdayName.equalsIgnoreCase(weekly.weekday())) {
                runCommands(world, date, weekly.actions());
            }
        }

        for (CustomEventsConfig.DatedEvent dated : custom.dated()) {
            if (matchesDated(dated.date(), date, calendar)) {
                runCommands(world, date, dated.actions());
            }
        }
    }

    private boolean matchesDated(String pattern, SeasonDate today, CalendarConfig calendar) {
        String[] parts = pattern.split("/");
        try {
            if (parts.length == 1) {
                int day = Integer.parseInt(parts[0].trim());
                return today.day() == calendar.clampDay(today.month(), day);
            } else if (parts.length == 2) {
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                return today.month() == month && today.day() == calendar.clampDay(month, day);
            } else if (parts.length == 3) {
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                int year = Integer.parseInt(parts[2].trim());
                return today.year() == year && today.month() == month && today.day() == calendar.clampDay(month, day);
            }
        } catch (NumberFormatException ignored) {
        }
        return false;
    }

    private void runCommands(World world, SeasonDate date, List<String> actions) {
        for (String action : actions) {
            String resolved = applyPlaceholders(action, world, date);
            if (resolved.startsWith("/")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved.substring(1));
            } else {
                String colored = ChatColor.translateAlternateColorCodes('&', resolved);
                for (Player player : world.getPlayers()) {
                    player.sendMessage(colored);
                }
            }
        }
    }

    private String applyPlaceholders(String text, World world, SeasonDate date) {
        Season season = plugin.calendarEngine().stateOf(world) != null ? plugin.calendarEngine().stateOf(world).season() : null;
        String monthName = plugin.config().calendar().monthDef(date.month()).name();
        String weekday = plugin.config().calendar().weekdays().get(date.weekdayIndex());

        String result = text
                .replace("%day%", String.valueOf(date.day()))
                .replace("%month%", String.valueOf(date.month()))
                .replace("%month_asname%", monthName)
                .replace("%year%", String.valueOf(date.year()))
                .replace("%weekday%", weekday)
                .replace("%season%", season != null ? season.name() : "")
                .replace("%world%", world.getName());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, result);
        }
        return result;
    }
}
