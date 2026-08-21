package io.github.kenvandine.solstice.command;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.config.Messages;
import io.github.kenvandine.solstice.storage.PlayerPrefs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Handles the five player-toggle commands (PLAN.md §3.7): toggleseasoncolors, toggletemperature,
 * toggleseasonparticles, togglefahrenheit, currentbiome. One executor for all five, dispatched on
 * command label — they're small and share the same "flip a PlayerPrefs field" shape.
 */
public final class ToggleCommands implements CommandExecutor {

    private final Solstice plugin;

    public ToggleCommands(Solstice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.config().messages();
        return switch (command.getName().toLowerCase()) {
            case "toggleseasoncolors" -> toggle(sender, messages, "solstice.toggleseasons",
                    PlayerPrefs::seasonColors, PlayerPrefs::withSeasonColors, "toggles.colors-on", "toggles.colors-off");
            case "toggleseasonparticles" -> toggle(sender, messages, "solstice.toggleparticles",
                    PlayerPrefs::seasonParticles, PlayerPrefs::withSeasonParticles, "toggles.particles-on", "toggles.particles-off");
            case "togglefahrenheit" -> toggle(sender, messages, "solstice.togglefahrenheit",
                    PlayerPrefs::fahrenheit, PlayerPrefs::withFahrenheit, "toggles.fahrenheit-on", "toggles.fahrenheit-off");
            case "toggletemperature" -> toggleTemperature(sender, messages, args);
            case "currentbiome" -> currentBiome(sender, messages);
            default -> false;
        };
    }

    private boolean toggle(CommandSender sender, Messages messages, String permission,
                            java.util.function.Predicate<PlayerPrefs> getter,
                            java.util.function.BiFunction<PlayerPrefs, Boolean, PlayerPrefs> setter,
                            String onKey, String offKey) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only"));
            return true;
        }
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(messages.get("general.no-permission"));
            return true;
        }
        var store = plugin.playerDataStore();
        PlayerPrefs current = store.get(player.getUniqueId());
        boolean newValue = !getter.test(current);
        store.set(player.getUniqueId(), setter.apply(current, newValue));
        sender.sendMessage(messages.get(newValue ? onKey : offKey));
        return true;
    }

    private boolean toggleTemperature(CommandSender sender, Messages messages, String[] args) {
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("solstice.toggletemperature.others")) {
                sender.sendMessage(messages.get("general.no-permission"));
                return true;
            }
            target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(messages.get("general.invalid-world"));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.get("general.player-only"));
                return true;
            }
            if (!sender.hasPermission("solstice.toggletemperature")) {
                sender.sendMessage(messages.get("general.no-permission"));
                return true;
            }
            target = player;
        }
        var store = plugin.playerDataStore();
        PlayerPrefs current = store.get(target.getUniqueId());
        boolean newValue = !current.temperatureDisplay();
        store.set(target.getUniqueId(), current.withTemperatureDisplay(newValue));
        sender.sendMessage(messages.get(newValue ? "toggles.temperature-on" : "toggles.temperature-off"));
        return true;
    }

    private boolean currentBiome(CommandSender sender, Messages messages) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only"));
            return true;
        }
        if (!sender.hasPermission("solstice.getbiome")) {
            sender.sendMessage(messages.get("general.no-permission"));
            return true;
        }
        String biomeName = player.getLocation().getBlock().getBiome().getKey().getKey();
        sender.sendMessage(messages.get("biome.current", Map.of("biome", biomeName)));
        return true;
    }
}
