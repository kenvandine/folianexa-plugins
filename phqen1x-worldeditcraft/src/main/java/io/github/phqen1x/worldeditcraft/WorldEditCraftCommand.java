package io.github.phqen1x.worldeditcraft;

import io.github.phqen1x.worldeditcraft.library.SchematicQuery;
import io.github.phqen1x.worldeditcraft.library.SchematicRecord;
import io.github.phqen1x.worldeditcraft.llm.LemonadeClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * {@code /wec} dispatch. Each branch checks its own permission node (see
 * {@code plugin.yml}) and hands off to {@link GenerationService}/{@link
 * io.github.phqen1x.worldeditcraft.library.SchematicLibrary} on the async
 * scheduler — nothing here touches a block or an entity, so nothing here
 * needs a region scheduler. Placement ({@code paste}/{@code preview}/
 * {@code undo}/{@code cancel}) is the one piece of the design not yet
 * implemented in this build — see the plugin README for milestone status.
 */
final class WorldEditCraftCommand implements CommandExecutor {

    private final WorldEditCraftPlugin plugin;

    WorldEditCraftCommand(WorldEditCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(usage(label));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "generate" -> handleGenerate(sender, label, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "rename" -> handleRename(sender, args);
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            case "regen", "preview", "paste", "undo", "cancel", "tag", "import", "export" ->
                    sender.sendMessage("'" + args[0] + "' isn't implemented in this build yet — see the plugin README's milestone status.");
            default -> sender.sendMessage(usage(label));
        }
        return true;
    }

    private void handleGenerate(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("worldeditcraft.generate")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " generate <prompt...>");
            return;
        }
        String brief = String.join(" ", List.of(args).subList(1, args.length));
        sender.sendMessage("Asking Lemonade to generate: " + brief);

        plugin.runAsync(() -> {
            GenerationService.GenerationResult result = plugin.generationService().generate(brief, null);
            if (result.success()) {
                sender.sendMessage("Generated '" + result.slug() + "' in " + result.attempts() + " attempt(s). "
                        + "Placement (/wec paste) isn't implemented in this build yet — the .schem file is in the library.");
            } else {
                sender.sendMessage("Generation failed after " + result.attempts() + " attempt(s): " + result.errorMessage());
            }
        });
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldeditcraft.library")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // fall back to page 1
            }
        }
        int finalPage = page;
        plugin.runAsync(() -> {
            List<SchematicRecord> records = plugin.library().list(new SchematicQuery(null, null, finalPage, 10));
            if (records.isEmpty()) {
                sender.sendMessage("No schematics on page " + finalPage + ".");
                return;
            }
            for (SchematicRecord record : records) {
                sender.sendMessage("- " + record.name() + " (" + record.width() + "x" + record.height() + "x" + record.length() + ")");
            }
        });
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldeditcraft.library")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /wec info <name>");
            return;
        }
        plugin.runAsync(() -> plugin.library().info(args[1]).ifPresentOrElse(
                record -> sender.sendMessage(record.name() + ": " + record.width() + "x" + record.height() + "x" + record.length()
                        + ", by " + record.model() + ", prompt: " + record.prompt()),
                () -> sender.sendMessage("No such schematic: '" + args[1] + "'.")
        ));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldeditcraft.library")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /wec delete <name>");
            return;
        }
        plugin.runAsync(() -> sender.sendMessage(plugin.library().delete(args[1])
                ? "Deleted '" + args[1] + "'."
                : "No such schematic: '" + args[1] + "'."));
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldeditcraft.library")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length != 3) {
            sender.sendMessage("Usage: /wec rename <name> <new>");
            return;
        }
        plugin.runAsync(() -> sender.sendMessage(plugin.library().rename(args[1], args[2])
                ? "Renamed '" + args[1] + "' to '" + args[2] + "'."
                : "Could not rename '" + args[1] + "' — no such schematic, or the new name is taken."));
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("worldeditcraft.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        plugin.runAsync(() -> {
            LemonadeClient.ModelsResult models = plugin.lemonadeClient().listModels();
            if (!models.success()) {
                sender.sendMessage("Lemonade  " + plugin.config().lemonade().baseUrl() + "  unreachable (" + models.errorMessage() + ")");
                return;
            }
            String configuredModel = plugin.config().lemonade().model();
            String effectiveModel = configuredModel.isBlank()
                    ? (models.models().isEmpty() ? "(none loaded)" : models.models().get(0))
                    : configuredModel;
            sender.sendMessage("Lemonade  " + plugin.config().lemonade().baseUrl() + "  reachable");
            sender.sendMessage("Model     " + effectiveModel + "  (" + models.models().size() + " model(s) available)");
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("worldeditcraft.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        plugin.runAsync(() -> {
            plugin.reloadWorldEditCraftConfig();
            sender.sendMessage("Phqen1xWorldEditCraft config reloaded.");
        });
    }

    private String usage(String label) {
        return "Usage: /" + label + " <generate|regen|list|info|preview|paste|undo|cancel|rename|tag|delete|import|export|status|reload>";
    }
}
