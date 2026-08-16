package io.github.kenvandine.hungergames;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates every configured arena's match: the once-a-second state
 * machine tick (see {@link #tick()}), and every bit of actual game state
 * this plugin touches (teleports, chest loot, potion effects, the world
 * border). {@link ArenaGame} decides *whether* a transition should happen;
 * this class is what actually *does* it.
 *
 * Each arena is expected to be its own dedicated Bukkit world (see
 * ArenaConfig's javadoc) — that lets the shrinking play area ride on the
 * real per-world {@code WorldBorder} API (native warning fog, push-back,
 * and damage, plus a smoothly animated shrink via {@code setSize(size,
 * seconds)}) instead of a hand-rolled per-tick distance/damage loop.
 *
 * Folia safety: the master tick loop runs on Bukkit's AsyncScheduler (it
 * only reads timestamps and compares them — no game state touched there,
 * same as FoliaNexaStatsPlugin's report loop). Every entity mutation
 * (teleport, inventory, gamemode, potion effects) is dispatched through
 * {@code Bukkit.getRegionScheduler()} against the player's *current*
 * location rather than assumed thread context, and cross-region/world
 * moves use {@code Player#teleportAsync} — see docs/plugin-dev/
 * 02-plugin-architecture.md in the FoliaNexa repo for this cluster's
 * Folia-safety rules. Block placement (chest loot) is grouped by chunk and
 * dispatched per-chunk, the same pattern SceneBuilder uses in the
 * campus-lobby plugin. World-border changes aren't tied to any single
 * region or entity, so they're dispatched via the global region scheduler.
 */
final class GameManager {

    private final HungerGamesPlugin plugin;
    private final Map<String, ArenaGame> games = new LinkedHashMap<>();
    private final Random random = new Random();

    private HungerGamesConfig config;
    private Object tickTaskHandle;

    GameManager(HungerGamesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        HungerGamesConfig newConfig = ConfigLoader.load(plugin.getConfig());
        Map<String, ArenaGame> next = new LinkedHashMap<>();

        for (ArenaConfig arena : newConfig.arenas()) {
            ArenaGame existing = games.get(arena.id());
            if (existing == null || existing.state() == GameState.WAITING) {
                next.put(arena.id(), new ArenaGame(arena));
            } else {
                plugin.getLogger().warning("Arena '" + arena.id()
                        + "' has a match in progress — its new config applies starting next match.");
                next.put(arena.id(), existing);
            }
        }
        for (Map.Entry<String, ArenaGame> entry : games.entrySet()) {
            if (!next.containsKey(entry.getKey()) && entry.getValue().state() != GameState.WAITING) {
                plugin.getLogger().warning("Arena '" + entry.getKey()
                        + "' was removed from config.yml but has a match in progress — letting it finish.");
                next.put(entry.getKey(), entry.getValue());
            }
        }

        games.clear();
        games.putAll(next);
        config = newConfig;

        if (config.arenas().isEmpty()) {
            plugin.getLogger().warning("No arenas are configured — add at least one under 'arenas:' in config.yml.");
        }
    }

    void startTicking() {
        tickTaskHandle = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> tick(), 1L, 1L, TimeUnit.SECONDS);
    }

    void stopTicking() {
        if (tickTaskHandle instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
            task.cancel();
        }
    }

    Optional<ArenaGame> findGameForTribute(UUID id) {
        return games.values().stream().filter(g -> g.isTribute(id)).findFirst();
    }

    // -- Commands ---------------------------------------------------------

    enum JoinResult {
        OK, NO_SUCH_ARENA, ALREADY_IN_A_GAME, GAME_IN_PROGRESS, ARENA_FULL
    }

    JoinResult join(String arenaId, Player player) {
        ArenaGame game = games.get(arenaId);
        if (game == null) {
            return JoinResult.NO_SUCH_ARENA;
        }
        if (findGameForTribute(player.getUniqueId()).isPresent()) {
            return JoinResult.ALREADY_IN_A_GAME;
        }
        if (!game.isJoinable()) {
            return JoinResult.GAME_IN_PROGRESS;
        }
        int capacity = Math.min(config.game().maxPlayers(), game.arena().spawnPoints().size());
        return game.addTribute(player.getUniqueId(), capacity) ? JoinResult.OK : JoinResult.ARENA_FULL;
    }

    boolean leave(Player player) {
        Optional<ArenaGame> maybeGame = findGameForTribute(player.getUniqueId());
        if (maybeGame.isEmpty()) {
            return false;
        }
        ArenaGame game = maybeGame.get();
        if (game.isJoinable()) {
            return game.removeTribute(player.getUniqueId());
        }
        if (game.isAlive(player.getUniqueId())) {
            recordElimination(game, player.getUniqueId(), null);
            return true;
        }
        return false;
    }

    boolean forceStart(String arenaId) {
        ArenaGame game = games.get(arenaId);
        if (game == null || game.tributes().isEmpty() || !game.isJoinable()) {
            return false;
        }
        beginMatch(game, Instant.now());
        return true;
    }

    boolean stop(String arenaId) {
        ArenaGame game = games.get(arenaId);
        if (game == null) {
            return false;
        }
        broadcast(game, "The match has been stopped by an admin.");
        returnToLobby(new ArrayList<>(game.tributes()), game);
        return true;
    }

    String[] describeArenas() {
        if (games.isEmpty()) {
            return new String[]{"No arenas are configured yet — add one under 'arenas:' in config.yml."};
        }
        List<String> lines = new ArrayList<>();
        lines.add("Arenas:");
        for (ArenaGame game : games.values()) {
            lines.add(" - " + game.arena().id() + " (" + game.arena().displayName() + "): "
                    + game.state() + ", " + game.tributes().size() + " tribute(s)");
        }
        return lines.toArray(new String[0]);
    }

    String[] describeTwists() {
        List<TwistConfig> pool = config.twists().pool();
        if (!config.twists().enabled() || pool.isEmpty()) {
            return new String[]{"Twists are disabled or none are configured."};
        }
        List<String> lines = new ArrayList<>();
        lines.add("Possible twists (" + Math.round(config.twists().chance() * 100) + "% chance one is chosen each match):");
        for (TwistConfig twist : pool) {
            lines.add(" - " + ChatColor.translateAlternateColorCodes('&', twist.displayName()) + ChatColor.RESET
                    + ": " + twist.description());
        }
        return lines.toArray(new String[0]);
    }

    // -- The tick loop ------------------------------------------------------

    private void tick() {
        Instant now = Instant.now();
        for (ArenaGame game : List.copyOf(games.values())) {
            switch (game.state()) {
                case WAITING -> {
                    if (game.tributes().size() >= config.game().minPlayers()) {
                        game.startCountdown(now, config.game().countdownSeconds());
                        broadcast(game, "Enough tributes! The games begin in " + config.game().countdownSeconds() + " seconds.");
                    }
                }
                case COUNTDOWN -> {
                    if (game.tributes().size() < config.game().minPlayers()) {
                        game.cancelCountdown();
                        broadcast(game, "Not enough tributes — countdown cancelled.");
                    } else if (game.countdownElapsed(now)) {
                        beginMatch(game, now);
                    }
                }
                case GRACE_PERIOD -> {
                    if (game.gracePeriodElapsed(now)) {
                        beginActivePhase(game, now);
                    }
                }
                case ACTIVE -> {
                    if (game.gameLengthElapsed(now)) {
                        beginDeathmatch(game);
                    }
                }
                case DEATHMATCH, ENDING -> {
                    // DEATHMATCH ends via elimination (see recordElimination); ENDING is
                    // handled synchronously by endMatch() the moment the match ends.
                }
            }
        }
    }

    // -- Match lifecycle ------------------------------------------------------

    private void beginMatch(ArenaGame game, Instant now) {
        Optional<TwistConfig> twist = TwistSelector.select(config.twists(), random);
        game.beginGracePeriod(now, twist.orElse(null), config.game());

        ArenaConfig arena = game.arena();
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            plugin.getLogger().warning("Arena '" + arena.id() + "': world '" + arena.world() + "' is not loaded — aborting this match.");
            broadcast(game, "This match could not start — its arena's world isn't loaded. Please try again later.");
            game.reset();
            return;
        }

        List<UUID> tributeList = new ArrayList<>(game.tributes());
        List<SpawnPoint> spawns = arena.spawnPoints();
        for (int i = 0; i < tributeList.size(); i++) {
            SpawnPoint sp = spawns.get(i % spawns.size());
            prepareAndTeleport(tributeList.get(i), new Location(world, sp.x(), sp.y(), sp.z(), sp.yaw(), sp.pitch()),
                    game.rules().potionEffects());
        }

        setInitialBorder(world, arena, game.rules());
        fillChests(world, arena, game.rules().lootMultiplier());

        String twistMessage = twist.map(t -> ChatColor.translateAlternateColorCodes('&',
                "&e&lTWIST: " + t.displayName() + " &r&7- " + t.description())).orElse(null);
        broadcast(game, "The games have begun! Grace period: " + game.rules().gracePeriodSeconds() + "s — no PvP until it ends.");
        if (twistMessage != null) {
            broadcast(game, twistMessage);
        }
    }

    private void beginActivePhase(ArenaGame game, Instant now) {
        game.beginActive(now);
        for (UUID id : game.alive()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> player.setInvulnerable(false));
            }
        }

        World world = Bukkit.getWorld(game.arena().world());
        if (world != null) {
            EffectiveRules rules = game.rules();
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> world.getWorldBorder().setSize(rules.finalBorderDiameter(), rules.gameLengthSeconds()));
        }
        broadcast(game, "Grace period is over — the border is closing in. Fight!");
    }

    private void beginDeathmatch(ArenaGame game) {
        game.beginDeathmatch();

        ArenaConfig arena = game.arena();
        World world = Bukkit.getWorld(arena.world());
        List<SpawnPoint> spawns = arena.deathmatchSpawnPoints().isEmpty() ? arena.spawnPoints() : arena.deathmatchSpawnPoints();
        List<UUID> survivors = new ArrayList<>(game.alive());

        if (world != null) {
            for (int i = 0; i < survivors.size(); i++) {
                SpawnPoint sp = spawns.get(i % spawns.size());
                teleportOnly(survivors.get(i), new Location(world, sp.x(), sp.y(), sp.z(), sp.yaw(), sp.pitch()));
            }
            EffectiveRules rules = game.rules();
            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                    world.getWorldBorder().setSize(rules.deathmatchBorderDiameter(), rules.deathmatchCountdownSeconds()));
        }
        broadcast(game, "Deathmatch! The " + survivors.size() + " remaining tributes have been pulled together for a final fight.");
    }

    /** Called by the listener on death/quit/forfeit. Returns true if this ended the match. */
    boolean recordElimination(ArenaGame game, UUID victim, UUID killer) {
        if (killer != null && !killer.equals(victim) && game.isTribute(killer)) {
            game.recordKill(killer);
        }
        boolean ended = game.eliminate(victim);
        if (ended) {
            endMatch(game);
        }
        return ended;
    }

    private void endMatch(ArenaGame game) {
        Optional<UUID> winnerId = game.winner();
        String message = winnerId.map(id -> {
            Player winner = Bukkit.getPlayer(id);
            return "&a&l" + (winner != null ? winner.getName() : "A tribute") + " has won the Hunger Games!";
        }).orElse("&7No survivors — the games end without a winner.");
        broadcast(game, ChatColor.translateAlternateColorCodes('&', message));

        List<UUID> participants = new ArrayList<>(game.tributes());
        long delayTicks = 20L * config.game().endingSeconds();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> returnToLobby(participants, game), Math.max(1, delayTicks));
    }

    private void returnToLobby(List<UUID> participants, ArenaGame game) {
        World lobbyWorld = Bukkit.getWorld(config.game().lobbyWorld());
        SpawnPoint sp = config.game().lobbySpawn();
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvulnerable(false);
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);
                player.getInventory().setItemInOffHand(null);
                if (lobbyWorld != null) {
                    player.teleportAsync(new Location(lobbyWorld, sp.x(), sp.y(), sp.z(), sp.yaw(), sp.pitch()));
                }
            });
        }
        game.reset();
    }

    // -- Per-tribute prep ---------------------------------------------------

    private void prepareAndTeleport(UUID id, Location dest, List<PotionEffectSpec> effects) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> {
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(true);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            for (PotionEffectType type : player.getActivePotionEffects().stream().map(PotionEffect::getType).toList()) {
                player.removePotionEffect(type);
            }
            player.teleportAsync(dest).thenRun(() -> applyEffects(id, effects));
        });
    }

    private void applyEffects(UUID id, List<PotionEffectSpec> effects) {
        if (effects.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> {
            for (PotionEffectSpec spec : effects) {
                // Legacy-but-still-supported accessor, same defensive
                // null-check-and-skip convention as SceneBuilder's
                // Material.matchMaterial() in the campus-lobby plugin —
                // an operator typo in config.yml shouldn't crash the match.
                PotionEffectType type = PotionEffectType.getByName(spec.type());
                if (type == null) {
                    plugin.getLogger().warning("Unknown potion effect type in twist config: " + spec.type());
                    continue;
                }
                player.addPotionEffect(new PotionEffect(type, spec.durationSeconds() * 20, spec.amplifier()));
            }
        });
    }

    private void teleportOnly(UUID id, Location dest) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> player.teleportAsync(dest));
    }

    private void broadcast(ArenaGame game, String message) {
        for (UUID id : game.tributes()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    // -- World border ---------------------------------------------------

    private void setInitialBorder(World world, ArenaConfig arena, EffectiveRules rules) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(arena.borderCenter().x(), arena.borderCenter().z());
            border.setWarningDistance(rules.borderWarningDistance());
            border.setDamageAmount(rules.borderDamageAmount());
            border.setDamageBuffer(rules.borderDamageBuffer());
            border.setSize(arena.initialBorderRadius() * 2.0);
        });
    }

    // -- Chest loot ---------------------------------------------------------

    private void fillChests(World world, ArenaConfig arena, double lootMultiplier) {
        List<LootEntry> pool = config.loot();
        int minItems = config.itemsPerChestMin();
        int maxItems = config.itemsPerChestMax();

        Map<Long, List<Point>> byChunk = new HashMap<>();
        for (Point chest : arena.chestLocations()) {
            int chunkX = ((int) Math.floor(chest.x())) >> 4;
            int chunkZ = ((int) Math.floor(chest.z())) >> 4;
            byChunk.computeIfAbsent(chunkKey(chunkX, chunkZ), key -> new ArrayList<>()).add(chest);
        }

        for (List<Point> chests : byChunk.values()) {
            Point first = chests.get(0);
            int chunkX = ((int) Math.floor(first.x())) >> 4;
            int chunkZ = ((int) Math.floor(first.z())) >> 4;
            Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, task -> {
                for (Point chest : chests) {
                    fillChest(world, chest, pool, minItems, maxItems, lootMultiplier);
                }
            });
        }
    }

    private void fillChest(World world, Point loc, List<LootEntry> pool, int minItems, int maxItems, double multiplier) {
        Block block = world.getBlockAt((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        if (!(block.getState() instanceof Chest chest)) {
            plugin.getLogger().warning("Configured chest location " + loc + " in world " + world.getName() + " isn't a chest — skipping.");
            return;
        }
        chest.getInventory().clear();

        int span = Math.max(1, maxItems - minItems + 1);
        int baseCount = minItems + random.nextInt(span);
        int count = (int) Math.round(baseCount * multiplier);

        List<LootEntry> picks = LootPicker.pickChestContents(pool, count, random);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < chest.getInventory().getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);

        int slotIndex = 0;
        for (LootEntry pick : picks) {
            if (slotIndex >= slots.size()) {
                break;
            }
            Material material = Material.matchMaterial(pick.material());
            if (material == null) {
                plugin.getLogger().warning("Unknown material in loot table: " + pick.material());
                continue;
            }
            int amount = Math.max(1, LootPicker.resolveAmount(pick, random));
            chest.getInventory().setItem(slots.get(slotIndex++), new ItemStack(material, amount));
        }
        chest.update(true, false);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
