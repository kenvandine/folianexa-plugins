package io.github.kenvandine.flowerwatch.command;

import io.github.kenvandine.flowerwatch.FlowerWatchPlugin;
import io.github.kenvandine.flowerwatch.config.FlowerWatchConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class FlowerWatchCommand implements CommandExecutor {

    private final FlowerWatchPlugin plugin;
    private final FlowerWatchConfig config;

    public FlowerWatchCommand(FlowerWatchPlugin plugin, FlowerWatchConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§6FlowerWatch §7— enabled=" + config.enabled()
                    + " densityScanEnabled=" + config.densityScanEnabled());
            sender.sendMessage("§7CoreProtect: configEnabled=" + config.coreProtectEnabled()
                    + " available=" + plugin.isCoreProtectAvailable());
            sender.sendMessage("§7Log file: " + plugin.getDataFolder().toPath().resolve(config.logFile()));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadFlowerWatch();
            sender.sendMessage("§aFlowerWatch config reloaded.");
            return true;
        }
        sender.sendMessage("§cUsage: /flowerwatch [status|reload]");
        return true;
    }
}
