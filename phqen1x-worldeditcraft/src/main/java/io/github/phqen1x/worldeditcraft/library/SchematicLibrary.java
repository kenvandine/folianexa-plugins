package io.github.phqen1x.worldeditcraft.library;

import io.github.phqen1x.worldeditcraft.llm.MiniJson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Owns the schematic library's directory: {@code <name>.schem} files, a
 * {@code <name>.json} sidecar per {@link SchematicRecord}, and a shared
 * {@code index.json} catalogue. No {@code org.bukkit} import, but this
 * does real file I/O — callers must only ever invoke it from {@code
 * Bukkit.getAsyncScheduler()}, never a game-tick thread (see
 * docs/phqen1x-rpg-suite/07-folia-safety.md, and {@code LemonadeClient}'s
 * class docs for the same contract stated the same way).
 */
public final class SchematicLibrary {

    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9_]+");
    private static final int MAX_SLUG_LENGTH = 64;

    private final Path directory;
    private final SchematicIndex index;

    private SchematicLibrary(Path directory, SchematicIndex index) {
        this.directory = directory;
        this.index = index;
    }

    /** Opens (creating if necessary) the library at {@code directory}, loading its {@code index.json} if present. */
    public static SchematicLibrary open(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path indexFile = directory.resolve("index.json");
        SchematicIndex index;
        if (Files.exists(indexFile)) {
            try {
                index = SchematicIndex.fromJson(Files.readString(indexFile));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            index = new SchematicIndex();
        }
        return new SchematicLibrary(directory, index);
    }

    /** Slugifies a candidate name: lowercase, {@code [a-z0-9_]}, truncated to 64 chars — see the DSL spec's {@code name} field rules. */
    public static String slugify(String candidate) {
        String slug = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            slug = "structure";
        }
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH);
        }
        return slug;
    }

    /** Returns the slug actually used (the requested one, or that name with a numeric suffix on a collision). */
    public String save(String requestedName, byte[] schematicBytes, SchematicRecord meta, List<MarkerRecord> markers) {
        String slug = SLUG_PATTERN.matcher(requestedName).matches() && requestedName.length() <= MAX_SLUG_LENGTH
                ? requestedName
                : slugify(requestedName);
        String finalSlug = slug;
        int suffix = 2;
        while (index.get(finalSlug).isPresent() && !index.get(finalSlug).get().checksum().equals(meta.checksum())) {
            finalSlug = slug + "_" + suffix;
            suffix++;
        }

        SchematicRecord finalMeta = finalSlug.equals(meta.name())
                ? meta
                : new SchematicRecord(finalSlug, meta.author(), meta.dateEpochMillis(), meta.prompt(), meta.model(),
                        meta.tags(), meta.checksum(), meta.width(), meta.height(), meta.length());

        try {
            Files.write(schemPath(finalSlug), schematicBytes);
            writeMarkers(finalSlug, markers);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        index.put(finalMeta);
        persistIndex();
        return finalSlug;
    }

    public Optional<byte[]> load(String name) {
        Path path = schemPath(name);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<SchematicRecord> info(String name) {
        return index.get(name);
    }

    /** Empty if {@code name} has no markers, or no such schematic at all. */
    public List<MarkerRecord> loadMarkers(String name) {
        Path path = markersPath(name);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return readMarkers(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<SchematicRecord> list(SchematicQuery query) {
        return index.search(query);
    }

    public int size() {
        return index.size();
    }

    public boolean delete(String name) {
        if (!index.remove(name)) {
            return false;
        }
        try {
            Files.deleteIfExists(schemPath(name));
            Files.deleteIfExists(markersPath(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        persistIndex();
        return true;
    }

    public boolean rename(String oldName, String newName) {
        if (!Files.exists(schemPath(oldName)) || !index.rename(oldName, newName)) {
            return false;
        }
        try {
            Files.move(schemPath(oldName), schemPath(newName));
            if (Files.exists(markersPath(oldName))) {
                Files.move(markersPath(oldName), markersPath(newName));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        persistIndex();
        return true;
    }

    private Path schemPath(String name) {
        return directory.resolve(name + ".schem");
    }

    private Path markersPath(String name) {
        return directory.resolve(name + ".markers.json");
    }

    private void writeMarkers(String name, List<MarkerRecord> markers) throws IOException {
        Path path = markersPath(name);
        if (markers.isEmpty()) {
            Files.deleteIfExists(path);
            return;
        }
        List<Object> entries = new ArrayList<>();
        for (MarkerRecord marker : markers) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", marker.id());
            entry.put("x", marker.x());
            entry.put("y", marker.y());
            entry.put("z", marker.z());
            entry.put("meta", marker.meta());
            entries.add(entry);
        }
        Files.writeString(path, MiniJson.write(Map.of("markers", entries)));
    }

    @SuppressWarnings("unchecked")
    private static List<MarkerRecord> readMarkers(String json) {
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("markers") instanceof List<?> list)) {
            return List.of();
        }
        List<MarkerRecord> markers = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> meta = map.get("meta") instanceof Map<?, ?> metaMap
                    ? (Map<String, Object>) metaMap
                    : Map.of();
            markers.add(new MarkerRecord(
                    String.valueOf(map.get("id")),
                    ((Number) map.get("x")).intValue(),
                    ((Number) map.get("y")).intValue(),
                    ((Number) map.get("z")).intValue(),
                    meta
            ));
        }
        return markers;
    }

    private void persistIndex() {
        try {
            Files.writeString(directory.resolve("index.json"), index.toJson());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
