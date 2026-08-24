package io.github.kenvandine.flowerwatch.log;

import io.github.kenvandine.flowerwatch.config.FlowerWatchConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Writes FlowerWatch's own structured, pipe-delimited diagnostic log —
 * deliberately separate from the server's console/latest.log so it's
 * easy to grep and to hand to someone else without wading through
 * unrelated server noise. One line per event/alert; see README.md for
 * the exact column layout and how to correlate a line here against
 * CoreProtect's own log.
 *
 * Uses a plain {@link FileHandler} (size-based rotation, {@code
 * java.util.logging}'s own built-in mechanism) rather than an async
 * write queue — simple and reliable, and appropriate for a plugin
 * that's meant to run temporarily to catch a live bug, not as a
 * permanent high-throughput addition. If event volume during an actual
 * flower burst turns out to cause noticeable overhead, lower
 * `coreprotect.max-lookups-per-minute` and disable whichever `events.*`
 * keys in config.yml you've already ruled out, rather than needing a
 * different logging mechanism.
 */
public final class DiagnosticLogger {

    private final Logger logger;
    private final Handler fileHandler;

    public DiagnosticLogger(Path dataFolder, FlowerWatchConfig config) throws IOException {
        Path logPath = dataFolder.resolve(config.logFile());
        Files.createDirectories(logPath.getParent());

        // A unique-per-instance logger name so a plugin reload (which
        // constructs a new DiagnosticLogger) doesn't double-register a
        // handler on some shared, JVM-wide logger.
        this.logger = Logger.getLogger("FlowerWatchDiagnostic-" + System.identityHashCode(this));
        this.logger.setUseParentHandlers(false);
        this.logger.setLevel(Level.ALL);

        long limitBytes = config.logMaxFileSizeMb() * 1024L * 1024L;
        FileHandler handler = new FileHandler(logPath.toString(), (int) limitBytes, config.logMaxFiles(), true);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();
            }
        });
        this.logger.addHandler(handler);
        this.fileHandler = handler;
    }

    /**
     * One flower-material block change. Columns: kind, timestamp, world,
     * "chunkX,chunkZ", "x,y,z", block type, cause (the Bukkit event
     * class, or "BlockFertilizeEvent(batch)" for a member of a bonemeal
     * burst), player (or "-"), free-form extra detail (or "-").
     */
    public void event(String world, int chunkX, int chunkZ, int x, int y, int z,
                       String blockType, String cause, String player, String extra) {
        write("EVENT", world, chunkX, chunkZ,
                x + "," + y + "," + z,
                blockType, cause, player == null ? "-" : player, extra == null ? "-" : extra);
    }

    /**
     * CoreProtect's own record(s) for the same block+coords, logged
     * immediately after the matching EVENT line above so the two can be
     * read side by side.
     */
    public void coreProtect(String world, int x, int y, int z, List<String> rows) {
        String joined = rows.isEmpty() ? "no matching CoreProtect entries" : String.join(" ~~ ", rows);
        write("COREPROTECT", world, x >> 4, z >> 4,
                x + "," + y + "," + z,
                "-", "-", "-", joined);
    }

    /** Logged once at startup (or reload) if CoreProtect isn't usable. */
    public void coreProtectUnavailable(String reason) {
        logger.info("COREPROTECT-STATUS|" + Instant.now() + "|" + reason);
    }

    /** A chunk's flower count jumped by more than the configured threshold since its last scan. */
    public void densityAlert(String world, int chunkX, int chunkZ, int count, int delta) {
        write("DENSITY-ALERT", world, chunkX, chunkZ, "-", "-", "-", "-",
                "count=" + count + " delta=+" + delta);
    }

    private void write(String kind, String world, int chunkX, int chunkZ,
                        String coords, String blockType, String cause, String player, String extra) {
        String line = String.join("|",
                kind, Instant.now().toString(), world,
                chunkX + "," + chunkZ, coords,
                blockType, cause, player, extra);
        logger.info(line);
    }

    public void close() {
        fileHandler.close();
        logger.removeHandler(fileHandler);
    }
}
