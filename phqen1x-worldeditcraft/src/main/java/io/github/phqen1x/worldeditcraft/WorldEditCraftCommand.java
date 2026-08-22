package io.github.phqen1x.worldeditcraft;

import io.github.phqen1x.worldeditcraft.library.SchematicQuery;
import io.github.phqen1x.worldeditcraft.library.SchematicRecord;
import io.github.phqen1x.worldeditcraft.llm.LemonadeClient;
import io.github.phqen1x.worldeditcraft.voxel.Transform;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /wec} dispatch. Each branch checks its own permission node (see
 * {@code plugin.yml}) and hands off to {@link GenerationService}/{@link
 * PasteService}/{@link io.github.phqen1x.worldeditcraft.library.SchematicLibrary}.
 * {@code generate}/{@code list}/{@code info}/{@code delete}/{@code rename}/
 * {@code status}/{@code reload}/{@code set} never touch a block or an
 * entity and run entirely on the async scheduler. {@code paste}/{@code
 * undo}/{@code cancel} hand off to {@link PasteService}, which is the
 * one place in this plugin that reaches the region scheduler — see its
 * class docs, and {@link io.github.phqen1x.worldeditcraft.paste.PasteEngine}'s,
 * for what's real versus what a live Folia server still needs to
 * confirm. {@code regen}/{@code preview}/{@code tag}/{@code import}/
 * {@code export} are the remaining pieces not yet implemented — see the
 * plugin README for milestone status. {@code set} is a small extension
 * beyond the design doc's own command table: it lets an operator point
 * at a different Lemonade host or switch models without hand-editing
 * {@code config.yml}.
 */
final class WorldEditCraftCommand implements CommandExecutor {

    /** Keys settable via {@code /wec set <key> <value>}, in the order shown in usage messages. */
    private static final List<String> SETTABLE_KEYS = List.of("base-url", "api-path", "model", "api-key");

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
            case "set" -> handleSet(sender, label, args);
            case "paste" -> handlePaste(sender, label, args);
            case "undo" -> handleUndo(sender);
            case "cancel" -> handleCancel(sender);
            case "regen", "preview", "tag", "import", "export" ->
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
            sender.sendMessage("Usage: /" + label + " generate <prompt...> [--paste]");
            return;
        }

        List<String> rest = new ArrayList<>(List.of(args).subList(1, args.length));
        boolean placeImmediately = rest.remove("--paste");
        String brief = String.join(" ", rest);
        if (brief.isBlank()) {
            sender.sendMessage("Usage: /" + label + " generate <prompt...> [--paste]");
            return;
        }
        if (placeImmediately && !(sender instanceof Player)) {
            sender.sendMessage("--paste needs a player to place at — generate without it and use /wec paste <name> instead.");
            return;
        }

        sender.sendMessage("Asking Lemonade to generate: " + brief);
        plugin.getLogger().info(() -> sender.getName() + " asked Lemonade to generate: " + brief);

        plugin.runAsync(() -> {
            GenerationService.GenerationResult result = plugin.generationService().generate(brief, null);
            if (!result.success()) {
                String failure = "Generation failed after " + result.attempts() + " attempt(s): " + result.errorMessage();
                sender.sendMessage(failure);
                plugin.getLogger().warning(failure);
                for (var issue : result.issues()) {
                    plugin.getLogger().warning(() -> "  [" + issue.severity() + "] op " + issue.opIndex() + ": " + issue.operatorMessage());
                }
                return;
            }
            plugin.getLogger().info(() -> "Generated '" + result.slug() + "' in " + result.attempts() + " attempt(s) for " + sender.getName() + ".");
            sender.sendMessage("Generated '" + result.slug() + "' in " + result.attempts() + " attempt(s).");
            if (placeImmediately) {
                PasteService.PasteRequest request = new PasteService.PasteRequest(result.slug(), (Player) sender, 0, Transform.Flip.NONE, false);
                plugin.pasteService().paste(request, sender::sendMessage);
            } else {
                sender.sendMessage("Use /wec paste " + result.slug() + " to place it.");
            }
        });
    }

    private void handlePaste(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("worldeditcraft.paste")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can paste — a paste places blocks at your current position.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " paste <name> [--rot 90|180|270] [--flip x|z] [--no-air]");
            return;
        }

        String name = args[1];
        int rotation = 0;
        Transform.Flip flip = Transform.Flip.NONE;
        boolean skipAir = false; // default: place air too, literally clearing the target volume to match the schematic

        for (int i = 2; i < args.length; i++) {
            switch (args[i].toLowerCase(Locale.ROOT)) {
                case "--rot" -> {
                    if (i + 1 >= args.length) {
                        sender.sendMessage("--rot needs a value (90, 180, or 270).");
                        return;
                    }
                    try {
                        rotation = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Invalid --rot value: '" + args[i] + "'.");
                        return;
                    }
                }
                case "--flip" -> {
                    if (i + 1 >= args.length) {
                        sender.sendMessage("--flip needs a value (x or z).");
                        return;
                    }
                    flip = switch (args[++i].toLowerCase(Locale.ROOT)) {
                        case "x" -> Transform.Flip.X;
                        case "z" -> Transform.Flip.Z;
                        default -> Transform.Flip.NONE;
                    };
                }
                case "--no-air" -> skipAir = true;
                default -> {
                    // ignore unrecognized flags rather than failing the whole command
                }
            }
        }

        if (rotation % 90 != 0) {
            sender.sendMessage("--rot must be a multiple of 90.");
            return;
        }

        PasteService.PasteRequest request = new PasteService.PasteRequest(name, player, rotation, flip, skipAir);
        plugin.runAsync(() -> plugin.pasteService().paste(request, sender::sendMessage));
    }

    private void handleUndo(CommandSender sender) {
        if (!sender.hasPermission("worldeditcraft.paste")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can undo — undo restores blocks in your current world.");
            return;
        }
        plugin.runAsync(() -> {
            boolean started = plugin.pasteService().undo(player, sender::sendMessage);
            if (!started) {
                sender.sendMessage("Nothing to undo in this world.");
            }
        });
    }

    private void handleCancel(CommandSender sender) {
        if (!sender.hasPermission("worldeditcraft.paste")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can cancel a paste.");
            return;
        }
        sender.sendMessage(plugin.pasteService().cancel(player)
                ? "Cancelling your in-flight paste..."
                : "You don't have an in-flight paste to cancel.");
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

    /**
     * {@code /wec set <key> <value>} — writes one {@code lemonade.*}
     * config.yml key to disk (via {@link WorldEditCraftPlugin#setConfigPath})
     * and reloads immediately, so an operator never has to hand-edit
     * {@code config.yml} just to point at a different Lemonade host or
     * switch models. Setting {@code model} to a non-blank value also
     * eagerly pulls and loads it right away, so a typo or an unavailable
     * model id is caught here rather than silently at the next {@code
     * /wec generate} (which pulls+loads the configured model itself
     * regardless — see {@code GenerationService#ensureModelReady} — this
     * is purely for immediate feedback).
     */
    private void handleSet(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("worldeditcraft.admin")) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + label + " set <" + String.join("|", SETTABLE_KEYS) + "> <value>");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        String path = lemonadeConfigPath(key);
        if (path == null) {
            sender.sendMessage("Unknown setting '" + key + "'. Valid keys: " + String.join(", ", SETTABLE_KEYS) + ".");
            return;
        }
        String value = String.join(" ", List.of(args).subList(2, args.length));

        plugin.runAsync(() -> {
            plugin.setConfigPath(path, value);
            sender.sendMessage("Set lemonade." + key + " = '" + value + "' and reloaded config.yml.");

            if (key.equals("model") && !value.isBlank()) {
                sender.sendMessage("Asking Lemonade to pull and load '" + value + "' — this can take a while on a first download...");
                LemonadeClient.ManagementResult pulled = plugin.lemonadeClient().pullModel(value);
                if (!pulled.success()) {
                    sender.sendMessage("Could not install '" + value + "': " + pulled.message());
                    return;
                }
                LemonadeClient.ManagementResult loaded = plugin.lemonadeClient().loadModel(value);
                sender.sendMessage(loaded.success()
                        ? "Model '" + value + "' is installed and loaded."
                        : "Installed '" + value + "' but could not load it: " + loaded.message());
            }
        });
    }

    private static String lemonadeConfigPath(String key) {
        return switch (key) {
            case "base-url" -> "lemonade.base-url";
            case "api-path" -> "lemonade.api-path";
            case "model" -> "lemonade.model";
            case "api-key" -> "lemonade.api-key";
            default -> null;
        };
    }

    private String usage(String label) {
        return "Usage: /" + label + " <generate|regen|list|info|preview|paste|undo|cancel|rename|tag|delete|import|export|status|reload|set>";
    }
}
