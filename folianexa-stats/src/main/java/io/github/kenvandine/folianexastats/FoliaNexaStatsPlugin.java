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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tracker = new StatsTracker();
        auraSkills = new AuraSkillsIntegration(getLogger());
        vaultEconomy = new VaultEconomyIntegration(getLogger());
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
        mgmtClient = new HttpMgmtClient(baseUrl, apiToken, getLogger());
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
        String uuid = player.getUniqueId().toString();
        tracker.onJoin(uuid, player.getName(), Instant.now());

        if (tracker.needsBaseline(uuid)) {
            Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> {
                HttpMgmtClient.PlayerBaseline baseline = mgmtClient.fetchPlayer(uuid).orElse(null);
                if (baseline == null) {
                    // Fetch failed (network/mgmt error, not a genuine "unknown
                    // player" 404 — see HttpMgmtClient#fetchPlayer's docs) —
                    // leave needsBaseline() true so the next join (or a future
                    // retry loop, if this matters enough to add one) tries
                    // again, rather than seeding a wrong zero baseline.
                    getLogger().warning("Could not load existing stats baseline for " + uuid + " — will not report for this player until it loads");
                    return;
                }
                tracker.applyBaseline(uuid, baseline.kills(), baseline.deaths(), baseline.blocksMined(), baseline.playtimeSecondsTotal());
            });
        }
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
        Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> mgmtClient.reportStats(tracker.drainReports()));
    }
}
