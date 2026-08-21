package io.github.kenvandine.folianexastats;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reports per-player kills/deaths/blocks-mined/playtime to
 * folia-nexa-mgmt's public player hub (PLAN.md §7A in the FoliaNexa
 * repo). Keep this class thin — wiring and Folia scheduling only. Real
 * logic lives in {@link StatsTracker} (plain, no Bukkit), and the two
 * softdepend integrations ({@link AuraSkillsIntegration},
 * {@link VaultEconomyIntegration}).
 */
public final class FoliaNexaStatsPlugin extends JavaPlugin {

    private StatsTracker tracker;
    private HttpMgmtClient mgmtClient;
    private AuraSkillsIntegration auraSkills;
    private VaultEconomyIntegration vaultEconomy;
    private Object reportTaskHandle;
    private String worldName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tracker = new StatsTracker();
        auraSkills = new AuraSkillsIntegration(getLogger());
        vaultEconomy = new VaultEconomyIntegration(getLogger());
        worldName = resolveWorldName();
        buildMgmtClient();

        getServer().getPluginManager().registerEvents(new FoliaNexaStatsListener(this), this);
        getCommand("foliaNexaStats").setExecutor(new FoliaNexaStatsCommand(this));

        // Anyone already online at (re)load — e.g. a /reload — still gets tracked.
        for (Player player : Bukkit.getOnlinePlayers()) {
            trackPlayer(player);
        }

        scheduleReportTask();
    }

    @Override
    public void onDisable() {
        // Paper cancels tasks scheduled through its own schedulers
        // (Bukkit.getRegionScheduler()/getGlobalRegionScheduler()/
        // getAsyncScheduler()) automatically on plugin disable — nothing
        // else to clean up here.
    }

    private void buildMgmtClient() {
        String baseUrl = getConfig().getString("mgmt-base-url", "");
        String apiToken = getConfig().getString("mgmt-api-token", "");
        if (baseUrl.isBlank()) {
            getLogger().warning("mgmt-base-url is not set in config.yml — stats reporting is disabled until it is.");
        }
        if (apiToken.isBlank()) {
            getLogger().warning("mgmt-api-token is not set in config.yml — stats reports will be rejected (401) until it is.");
        }
        mgmtClient = new HttpMgmtClient(baseUrl, apiToken, worldName, getLogger());
    }

    /**
     * folia-nexa-node sets FOLIA_WORLD_NAME on this JVM's environment
     * (see runner.py in the FoliaNexa repo) so every report can be
     * attributed to the specific world it came from — this plugin is
     * default_for_all_worlds, so a player is routinely tracked by more
     * than one world's independent plugin instance at once; without
     * per-world attribution, mgmt has no way to tell those apart (see
     * mgmt/src/folia_mgmt/routers/stats.py). Missing/blank means either
     * a bare dev/test server (not run through folia-nexa-node at all) or
     * a not-yet-upgraded node-agent build that predates this env var —
     * reports still work either way, just bucketed together with any
     * other unidentified world's contributions until node is upgraded
     * too (mgmt sums per-world reports, so this degrades the same way a
     * not-yet-upgraded plugin build does, not a hard failure).
     */
    private String resolveWorldName() {
        String fromEnv = System.getenv("FOLIA_WORLD_NAME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        getLogger().warning(
                "FOLIA_WORLD_NAME is not set — this world's stats reports won't be attributed "
                        + "separately from other unidentified worlds until folia-nexa-node is upgraded.");
        return "unidentified-world";
    }

    void reloadPluginConfig() {
        reloadConfig();
        buildMgmtClient();
        scheduleReportTask(); // pick up a changed report-interval-seconds
    }

    private void scheduleReportTask() {
        if (reportTaskHandle instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
            task.cancel();
        }
        long intervalSeconds = Math.max(1, getConfig().getLong("report-interval-seconds", 60));
        reportTaskHandle = Bukkit.getAsyncScheduler().runAtFixedRate(
                this,
                scheduledTask -> runReportCycle(),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    void triggerReportNow() {
        Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> runReportCycle());
    }

    /** Called on join, and once for each already-online player at plugin enable. */
    void trackPlayer(Player player) {
        tracker.onJoin(player.getUniqueId().toString(), player.getName(), Instant.now());
    }

    StatsTracker getTracker() {
        return tracker;
    }

    /**
     * One report cycle: flush playtime and gather softdepend readings for
     * every online player on THEIR OWN region thread (this touches other
     * plugins' live state, which this codebase's Folia-safety rules
     * treat as needing the same thread-ownership discipline as touching
     * game state directly — see AuraSkillsIntegration/
     * VaultEconomyIntegration's docs), then hop to the async scheduler
     * once for the actual HTTP report.
     */
    private void runReportCycle() {
        Instant now = Instant.now();
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (online.isEmpty()) {
            sendReportAsync();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(online.size());
        for (Player player : online) {
            Bukkit.getRegionScheduler().run(this, player.getLocation(), scheduledTask -> {
                try {
                    gatherOnlinePlayerStats(player, now);
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        sendReportAsync();
                    }
                }
            });
        }
    }

    private void gatherOnlinePlayerStats(Player player, Instant now) {
        String uuid = player.getUniqueId().toString();
        tracker.flushPlaytime(uuid, now);

        Map<String, Double> extra = new HashMap<>();
        auraSkills.getPowerLevel(player).ifPresent(level -> extra.put("auraskills_power_level", (double) level));
        if (Bukkit.getPluginManager().isPluginEnabled("AxAuctions")) {
            vaultEconomy.getBalance(player).ifPresent(balance -> extra.put("axauctions_wealth", balance));
        }
        if (!extra.isEmpty()) {
            tracker.recordExtraStats(uuid, extra);
        }
    }

    private void sendReportAsync() {
        Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> {
            List<StatsTracker.PlayerReport> reports = tracker.snapshotReports();
            // Only clear the reported deltas once mgmt actually has them —
            // see StatsTracker#confirmReported's docs for why clearing
            // eagerly would lose progress on a failed send instead of just
            // reporting it a cycle late.
            if (mgmtClient.reportStats(reports)) {
                tracker.confirmReported(reports);
            }
        });
    }
}
