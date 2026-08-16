package io.github.kenvandine.hungergames;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

final class HungerGamesCommand implements CommandExecutor {

    private final HungerGamesPlugin plugin;

    HungerGamesCommand(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(usage(label));
            return true;
        }

        GameManager manager = plugin.getGameManager();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> handleJoin(sender, label, args, manager);
            case "leave" -> handleLeave(sender, manager);
            case "list", "arenas" -> sender.sendMessage(manager.describeArenas());
            case "twists" -> sender.sendMessage(manager.describeTwists());
            case "forcestart" -> handleForceStart(sender, label, args, manager);
            case "stop" -> handleStop(sender, label, args, manager);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(usage(label));
        }
        return true;
    }

    private void handleJoin(CommandSender sender, String label, String[] args, GameManager manager) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can join a game.");
            return;
        }
        if (!player.hasPermission("hungergames.join")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " join <arena>");
            return;
        }
        GameManager.JoinResult result = manager.join(args[1], player);
        sender.sendMessage(switch (result) {
            case OK -> "You joined arena '" + args[1] + "'. Waiting for more tributes...";
            case NO_SUCH_ARENA -> "No such arena: '" + args[1] + "'. See /" + label + " list.";
            case ALREADY_IN_A_GAME -> "You're already queued or playing in a game — leave it first.";
            case GAME_IN_PROGRESS -> "That arena's match is already in progress.";
            case ARENA_FULL -> "That arena is full.";
        });
    }

    private void handleLeave(CommandSender sender, GameManager manager) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can leave a game.");
            return;
        }
        sender.sendMessage(manager.leave(player) ? "You left the game." : "You're not in a game.");
    }

    private void handleForceStart(CommandSender sender, String label, String[] args, GameManager manager) {
        if (!sender.hasPermission("hungergames.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " forcestart <arena>");
            return;
        }
        sender.sendMessage(manager.forceStart(args[1])
                ? "Forced arena '" + args[1] + "' to start."
                : "Could not force-start '" + args[1] + "' — no such arena, or no tributes queued.");
    }

    private void handleStop(CommandSender sender, String label, String[] args, GameManager manager) {
        if (!sender.hasPermission("hungergames.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " stop <arena>");
            return;
        }
        sender.sendMessage(manager.stop(args[1]) ? "Stopped arena '" + args[1] + "'." : "No such arena: '" + args[1] + "'.");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("hungergames.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        plugin.reloadHungerGamesConfig();
        sender.sendMessage("HungerGames config reloaded.");
    }

    private String usage(String label) {
        return "Usage: /" + label + " <join|leave|list|twists|forcestart|stop|reload> [arena]";
    }
}
