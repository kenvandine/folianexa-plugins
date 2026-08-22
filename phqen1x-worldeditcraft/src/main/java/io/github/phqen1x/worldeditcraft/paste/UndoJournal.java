package io.github.phqen1x.worldeditcraft.paste;

import io.github.phqen1x.worldeditcraft.llm.MiniJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records the block state at every position a paste is about to
 * overwrite, in the order those overwrites happen, and can replay them
 * in reverse to undo a paste — {@code /wec undo}. No {@code org.bukkit}
 * import, but this does real file I/O; {@link PasteEngine} captures
 * entries on the region thread that's about to do the overwrite (per
 * docs/phqen1x-rpg-suite/07-folia-safety.md: "Undo reads happen on the
 * owning thread... never read blocks from the async side") and hands
 * the finished list to this class to write, which callers must do from
 * {@code Bukkit.getAsyncScheduler()}.
 *
 * <p>Replaying in reverse is what makes a position overwritten twice in
 * one paste (once by pass 1, again by pass 2) undo correctly — the last
 * entry recorded for that position is also the first one replayed,
 * restoring what was there before pass 1 ever touched it.
 */
public final class UndoJournal {

    /** One captured block state — {@code block} is the same canonical string {@link io.github.phqen1x.worldeditcraft.voxel.BlockStateRef} produces. */
    public record Entry(int x, int y, int z, String block) {
    }

    private UndoJournal() {
    }

    public static void write(Path file, List<Entry> entries) {
        List<Object> serialized = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("x", entry.x());
            map.put("y", entry.y());
            map.put("z", entry.z());
            map.put("block", entry.block());
            serialized.add(map);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MiniJson.write(Map.of("entries", serialized)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Entry> read(Path file) {
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parse(json);
    }

    static List<Entry> parse(String json) {
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("entries") instanceof List<?> list)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                entries.add(new Entry(
                        ((Number) map.get("x")).intValue(),
                        ((Number) map.get("y")).intValue(),
                        ((Number) map.get("z")).intValue(),
                        String.valueOf(map.get("block"))
                ));
            }
        }
        return entries;
    }

    /** {@code entries} replayed in reverse — the order that correctly undoes a position overwritten more than once. */
    public static List<Entry> replayOrder(List<Entry> entries) {
        List<Entry> reversed = new ArrayList<>(entries);
        java.util.Collections.reverse(reversed);
        return reversed;
    }
}
