package io.github.kenvandine.solstice.command;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.config.Messages;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** {@code /season}: current season, date, time, days until next season, active events. */
public final class SeasonCommand implements CommandExecutor {

    private final Solstice plugin;

    public SeasonCommand(Solstice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.config().messages();
        if (!sender.hasPermission("solstice.getinfo")) {
            sender.sendMessage(messages.get("general.no-permission"));
            return true;
        }

        World world = args.length > 0 ? plugin.getServer().getWorld(args[0])
                : (sender instanceof Player player ? player.getWorld() : firstManagedWorld());
        if (world == null || !plugin.calendarEngine().isManaged(world)) {
            sender.sendMessage(messages.get("general.invalid-world"));
            return true;
        }

        WorldSeasonState state = plugin.calendarEngine().stateOf(world);
        String date = state.date().day() + " " + plugin.config().calendar().monthDef(state.date().month()).name()
                + " " + state.date().year();
        var api = io.github.kenvandine.solstice.api.SolsticeAPI.getInstance();
        String time = String.format("%02d:%02d", api.getHours(world), api.getMinutes(world));
        var derived = io.github.kenvandine.solstice.season.SeasonManager.derive(
                plugin.config().main(), plugin.config().calendar(), state.date());

        sender.sendMessage(messages.get("season.info", Map.of(
                "season", capitalize(state.season().name()),
                "date", date,
                "time", time,
                "days", String.valueOf(derived.daysUntilNextSeason())
        )));

        var events = plugin.eventManager().activeEventNames(world);
        if (events.isEmpty()) {
            sender.sendMessage(messages.get("season.no-active-events"));
        } else {
            sender.sendMessage(messages.get("season.active-events", Map.of("events", String.join(", ", events))));
        }
        return true;
    }

    private World firstManagedWorld() {
        for (World world : plugin.getServer().getWorlds()) {
            if (plugin.calendarEngine().isManaged(world)) {
                return world;
            }
        }
        return null;
    }

    private String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
