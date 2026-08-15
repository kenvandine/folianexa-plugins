package io.github.kenvandine.folianexastats;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

final class FoliaNexaStatsCommand implements CommandExecutor {

    private final FoliaNexaStatsPlugin plugin;

    FoliaNexaStatsCommand(FoliaNexaStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("foliaNexaStats.reload")) {
                sender.sendMessage("You don't have permission to do that.");
                return true;
            }
            plugin.reloadPluginConfig();
            sender.sendMessage("FoliaNexaStats config reloaded.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("report")) {
            if (!sender.hasPermission("foliaNexaStats.report")) {
                sender.sendMessage("You don't have permission to do that.");
                return true;
            }
            plugin.triggerReportNow();
            sender.sendMessage("Triggered an immediate stats report.");
            return true;
        }
        sender.sendMessage("Usage: /" + label + " <reload|report>");
        return true;
    }
}
