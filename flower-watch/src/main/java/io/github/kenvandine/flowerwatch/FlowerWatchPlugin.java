package io.github.kenvandine.flowerwatch;

import io.github.kenvandine.flowerwatch.command.FlowerWatchCommand;
import io.github.kenvandine.flowerwatch.config.FlowerWatchConfig;
import io.github.kenvandine.flowerwatch.coreprotect.CoreProtectBridge;
import io.github.kenvandine.flowerwatch.listener.FlowerChangeListener;
import io.github.kenvandine.flowerwatch.log.DiagnosticLogger;
import io.github.kenvandine.flowerwatch.scan.DensityScanner;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

/**
 * FlowerWatch: temporary, observation-only diagnostic instrumentation
 * for tracking down a cluster-wide bug where flowers spawn very densely
 * and get destroyed in a way CoreProtect's own log alone can't explain
 * (it records that a block changed, not why). See README.md.
 *
 * This plugin never cancels, blocks, or otherwise changes any game
 * behavior — it only logs. Uninstalling it (or setting {@code enabled:
 * false} in config.yml) has zero effect on whatever is actually causing
 * the flower bug.
 */
public final class FlowerWatchPlugin extends JavaPlugin {

    private FlowerWatchConfig config;
    private DiagnosticLogger diagnosticLogger;
    private DensityScanner densityScanner;
    private CoreProtectBridge coreProtect;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new FlowerWatchConfig(this);

        if (!config.enabled()) {
            getLogger().warning("FlowerWatch is disabled in config.yml (enabled: false) — "
                    + "no listeners registered, no scanning. This is observation-only "
                    + "instrumentation; nothing else in this plugin does anything while disabled.");
            return;
        }

        try {
            diagnosticLogger = new DiagnosticLogger(getDataFolder().toPath(), config);
        } catch (IOException e) {
            getLogger().severe("FlowerWatch could not open its diagnostic log file, disabling: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        coreProtect = new CoreProtectBridge(getLogger());
        initializeCoreProtect();

        FixedWindowRateLimiter coreProtectRateLimiter = new FixedWindowRateLimiter(
                config.coreProtectMaxLookupsPerMinute(), 60, Clock.systemUTC());

        FlowerChangeListener listener = new FlowerChangeListener(config, diagnosticLogger, coreProtect, coreProtectRateLimiter);
        getServer().getPluginManager().registerEvents(listener, this);

        densityScanner = new DensityScanner(this, config, diagnosticLogger);
        densityScanner.start();

        Objects.requireNonNull(getCommand("flowerwatch"), "flowerwatch command missing from plugin.yml")
                .setExecutor(new FlowerWatchCommand(this, config));

        getLogger().info("FlowerWatch enabled — diagnostic log: "
                + getDataFolder().toPath().resolve(config.logFile()));
    }

    @Override
    public void onDisable() {
        if (densityScanner != null) {
            densityScanner.stop();
        }
        if (diagnosticLogger != null) {
            diagnosticLogger.close();
        }
    }

    public boolean isCoreProtectAvailable() {
        return coreProtect != null && coreProtect.isAvailable();
    }

    /** Reloads config.yml and re-applies anything that isn't already read live. */
    public void reloadFlowerWatch() {
        reloadConfig();
        if (densityScanner != null) {
            densityScanner.stop();
            densityScanner.start();
        }
        initializeCoreProtect();
    }

    private void initializeCoreProtect() {
        if (coreProtect == null || !config.coreProtectEnabled()) {
            return;
        }
        coreProtect.initialize().ifPresentOrElse(
                reason -> {
                    getLogger().info("FlowerWatch: CoreProtect cross-referencing unavailable ("
                            + reason + ") — logging FlowerWatch's own events without it.");
                    if (diagnosticLogger != null) {
                        diagnosticLogger.coreProtectUnavailable(reason);
                    }
                },
                () -> getLogger().info("FlowerWatch: CoreProtect found, cross-referencing enabled.")
        );
    }
}
