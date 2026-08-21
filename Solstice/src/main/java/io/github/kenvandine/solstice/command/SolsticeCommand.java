package io.github.kenvandine.solstice.command;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.SeasonDate;
import io.github.kenvandine.solstice.config.CalendarConfig;
import io.github.kenvandine.solstice.config.Messages;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * {@code /solstice} (alias {@code /sol}) admin command root — one node, {@code solstice.admin}
 * (PLAN.md §3.7). Subcommands that need a world default to the sender's current world if they're
 * a player, or the first Solstice-managed world if run from console.
 */
public final class SolsticeCommand implements CommandExecutor {

    private final Solstice plugin;

    public SolsticeCommand(Solstice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.config().messages();
        if (!sender.hasPermission("solstice.admin")) {
            sender.sendMessage(messages.get("general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, messages);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "set" -> handleSet(sender, messages, rest);
            case "setdate" -> handleSetDate(sender, messages, rest);
            case "nextseason" -> handleNextSeason(sender, messages);
            case "pausetime" -> handlePauseTime(sender, messages);
            case "temperature" -> handleTemperature(sender, messages, rest);
            case "restoreworld" -> handleRestoreWorld(sender, messages);
            case "disable" -> handleDisable(sender, messages);
            case "install" -> handleInstall(sender, messages, rest);
            case "getinfo" -> new SeasonCommand(plugin).onCommand(sender, command, label, new String[0]);
            case "reload" -> handleReload(sender, messages);
            case "help" -> {
                sendHelp(sender, messages);
                yield true;
            }
            default -> {
                sender.sendMessage(messages.get("general.unknown-command"));
                yield true;
            }
        };
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        for (World world : plugin.getServer().getWorlds()) {
            if (plugin.calendarEngine().isManaged(world)) {
                return world;
            }
        }
        return null;
    }

    private boolean handleSet(CommandSender sender, Messages messages, String[] args) {
        World world = resolveWorld(sender);
        if (world == null || args.length < 1) {
            sender.sendMessage(messages.get("general.invalid-world"));
            return true;
        }
        Season season = parseSeason(args[0]);
        if (season == null) {
            sender.sendMessage(messages.get("general.unknown-command"));
            return true;
        }
        plugin.calendarEngine().setSeason(world, season);
        sender.sendMessage(messages.get("season.set", Map.of("world", world.getName(), "season", season.name())));
        return true;
    }

    private Season parseSeason(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "spring" -> Season.SPRING;
            case "summer" -> Season.SUMMER;
            case "autumn", "fall" -> Season.AUTUMN;
            case "winter" -> Season.WINTER;
            default -> null;
        };
    }

    private boolean handleSetDate(CommandSender sender, Messages messages, String[] args) {
        World world = resolveWorld(sender);
        if (world == null || args.length < 1) {
            sender.sendMessage(messages.get("general.invalid-world"));
            return true;
        }
        String[] parts = args[0].split("/");
        if (parts.length != 3) {
            sender.sendMessage(messages.get("general.unknown-command"));
            return true;
        }
        try {
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            CalendarConfig calendar = plugin.config().calendar();
            SeasonDate date = calendar.dateFromTotalDays(calendar.totalDaysFor(year, month, calendar.clampDay(month, day)));
            plugin.calendarEngine().setDate(world, date);
            sender.sendMessage(messages.get("season.date-set", Map.of("world", world.getName(),
                    "date", day + "/" + month + "/" + year)));
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get("general.unknown-command"));
        }
        return true;
    }

    private boolean handleNextSeason(CommandSender sender, Messages messages) {
        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage(messages.get("general.invalid-world"));
            return true;
        }
        plugin.calendarEngine().nextSeason(world);
        var state = plugin.calendarEngine().stateOf(world);
        sender.sendMessage(messages.get("season.advanced", Map.of("world", world.getName(),
                "season", state != null ? state.season().name() : "?")));
        return true;
    }

    private boolean handlePauseTime(CommandSender sender, Messages messages) {
        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage(messages.get("general.invalid-world"));
            return true;
        }
        var state = plugin.calendarEngine().stateOf(world);
        boolean newPaused = state == null || !state.timePaused();
        plugin.calendarEngine().setPaused(world, newPaused);
        sender.sendMessage(messages.get(newPaused ? "season.paused" : "season.unpaused", Map.of("world", world.getName())));
        return true;
    }

    private boolean handleTemperature(CommandSender sender, Messages messages, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("general.unknown-command"));
            return true;
        }
        var temperatureManager = plugin.temperatureManager();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "toggle" -> {
                boolean enabled = !temperatureManager.isRuntimeEnabled();
                temperatureManager.setRuntimeEnabled(enabled);
                sender.sendMessage(messages.get(enabled ? "toggles.temperature-on" : "toggles.temperature-off"));
            }
            case "modify" -> {
                if (args.length < 4) {
                    sender.sendMessage(messages.get("general.unknown-command"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(messages.get("general.invalid-world"));
                    return true;
                }
                try {
                    double delta = Double.parseDouble(args[2]);
                    int seconds = Integer.parseInt(args[3]);
                    temperatureManager.applyTimedTemperatureEffect(target, delta, seconds);
                } catch (NumberFormatException e) {
                    sender.sendMessage(messages.get("general.unknown-command"));
                }
            }
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage(messages.get("general.unknown-command"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(messages.get("general.invalid-world"));
                    return true;
                }
                temperatureManager.clearEffects(target.getUniqueId());
            }
            default -> sender.sendMessage(messages.get("general.unknown-command"));
        }
        return true;
    }

    private boolean handleRestoreWorld(CommandSender sender, Messages messages) {
        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage(messages.get("general.invalid-world"));
            return true;
        }
        sender.sendMessage(messages.get("admin.restore-started", Map.of("world", world.getName())));
        plugin.snowManager().forceRevertAll(world);
        plugin.floraManager().forceRevertAll(world);
        sender.sendMessage(messages.get("admin.restore-done", Map.of("world", world.getName())));
        return true;
    }

    private boolean handleDisable(CommandSender sender, Messages messages) {
        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage(messages.get("general.invalid-world"));
            return true;
        }
        plugin.calendarEngine().disableWorld(world);
        sender.sendMessage(messages.get("admin.disabled", Map.of("world", world.getName())));
        return true;
    }

    private boolean handleInstall(CommandSender sender, Messages messages, String[] args) {
        // World-generator-level seasonal terrain hooks are out of scope for this build (see README).
        sender.sendMessage(messages.get("general.unknown-command"));
        sender.sendMessage("§7`install` needs a custom world-generator integration that isn't implemented "
                + "in this build — see the README's documented gaps.");
        return true;
    }

    private boolean handleReload(CommandSender sender, Messages messages) {
        plugin.config().reload();
        sender.sendMessage(plugin.config().messages().get("general.reloaded"));
        return true;
    }

    private void sendHelp(CommandSender sender, Messages messages) {
        sender.sendMessage("§b/solstice set <spring|summer|fall|winter>");
        sender.sendMessage("§b/solstice setdate <dd/mm/yyyy>");
        sender.sendMessage("§b/solstice nextseason");
        sender.sendMessage("§b/solstice pausetime");
        sender.sendMessage("§b/solstice temperature <toggle|modify <player> <delta> <seconds>|clear <player>>");
        sender.sendMessage("§b/solstice restoreworld");
        sender.sendMessage("§b/solstice disable");
        sender.sendMessage("§b/solstice getinfo");
        sender.sendMessage("§b/solstice reload");
    }
}
