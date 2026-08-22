package io.github.phqen1x.worldeditcraft.paste;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One in-flight paste: id, progress counters, and a cancel flag. Plain
 * counters, no {@code org.bukkit} import — {@link PasteEngine} mutates
 * this from several region threads at once (one per chunk), so every
 * field is a concurrency-safe primitive rather than something needing
 * external synchronization.
 */
public final class PasteJob {

    private final UUID id = UUID.randomUUID();
    private final int totalPlacements;
    private final long startMillis = System.currentTimeMillis();
    private final AtomicInteger placed = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public PasteJob(int totalPlacements) {
        this.totalPlacements = totalPlacements;
    }

    public UUID id() {
        return id;
    }

    /** Batches check this before running and unwind rather than placing anything once it's set. */
    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    void addPlaced(int count) {
        placed.addAndGet(count);
    }

    public PasteProgress progress() {
        return new PasteProgress(placed.get(), totalPlacements, System.currentTimeMillis() - startMillis);
    }
}
