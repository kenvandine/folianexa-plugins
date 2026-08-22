package io.github.phqen1x.worldeditcraft.library;

import io.github.phqen1x.worldeditcraft.llm.MiniJson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The in-memory schematic catalogue, serialized to {@code index.json}.
 * No {@code org.bukkit} import and no file I/O — {@link SchematicLibrary}
 * owns loading/saving the JSON this class produces/consumes.
 *
 * <p>{@link #put} de-duplicates by checksum: adding a record whose
 * checksum matches an existing entry replaces that entry (under its
 * original name) rather than creating a second catalogue row for
 * byte-identical content — the case {@code /wec regen} can produce when
 * a repair round or a re-run happens to rasterize to the same bytes.
 */
public final class SchematicIndex {

    private final Map<String, SchematicRecord> byName = new LinkedHashMap<>();

    public void put(SchematicRecord record) {
        String existingNameWithSameChecksum = byName.values().stream()
                .filter(r -> !r.name().equals(record.name()) && r.checksum().equals(record.checksum()))
                .map(SchematicRecord::name)
                .findFirst()
                .orElse(null);
        if (existingNameWithSameChecksum != null) {
            byName.remove(existingNameWithSameChecksum);
        }
        byName.put(record.name(), record);
    }

    public boolean remove(String name) {
        return byName.remove(name) != null;
    }

    public boolean rename(String oldName, String newName) {
        SchematicRecord record = byName.remove(oldName);
        if (record == null) {
            return false;
        }
        byName.put(newName, new SchematicRecord(newName, record.author(), record.dateEpochMillis(), record.prompt(),
                record.model(), record.tags(), record.checksum(), record.width(), record.height(), record.length()));
        return true;
    }

    public Optional<SchematicRecord> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public int size() {
        return byName.size();
    }

    public List<SchematicRecord> search(SchematicQuery query) {
        List<SchematicRecord> matches = new ArrayList<>();
        for (SchematicRecord record : byName.values()) {
            if (query.tag() != null && !record.tags().contains(query.tag())) {
                continue;
            }
            if (query.search() != null && !query.search().isBlank()
                    && !record.name().toLowerCase(java.util.Locale.ROOT).contains(query.search().toLowerCase(java.util.Locale.ROOT))
                    && !record.prompt().toLowerCase(java.util.Locale.ROOT).contains(query.search().toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            matches.add(record);
        }
        matches.sort(Comparator.comparingLong(SchematicRecord::dateEpochMillis).reversed());

        int fromIndex = Math.min((query.page() - 1) * query.pageSize(), matches.size());
        int toIndex = Math.min(fromIndex + query.pageSize(), matches.size());
        return List.copyOf(matches.subList(fromIndex, toIndex));
    }

    public String toJson() {
        List<Object> entries = new ArrayList<>();
        for (SchematicRecord record : byName.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", record.name());
            entry.put("author", record.author());
            entry.put("date", record.dateEpochMillis());
            entry.put("prompt", record.prompt());
            entry.put("model", record.model());
            entry.put("tags", record.tags());
            entry.put("checksum", record.checksum());
            entry.put("width", record.width());
            entry.put("height", record.height());
            entry.put("length", record.length());
            entries.add(entry);
        }
        return MiniJson.write(Map.of("schematics", entries));
    }

    @SuppressWarnings("unchecked")
    public static SchematicIndex fromJson(String json) {
        SchematicIndex index = new SchematicIndex();
        Object parsed = MiniJson.parse(json);
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("schematics") instanceof List<?> list)) {
            return index;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            List<String> tags = new ArrayList<>();
            if (map.get("tags") instanceof List<?> tagList) {
                for (Object tag : tagList) {
                    tags.add(String.valueOf(tag));
                }
            }
            index.put(new SchematicRecord(
                    String.valueOf(map.get("name")),
                    String.valueOf(map.get("author")),
                    ((Number) map.get("date")).longValue(),
                    String.valueOf(map.get("prompt")),
                    String.valueOf(map.get("model")),
                    tags,
                    String.valueOf(map.get("checksum")),
                    ((Number) map.get("width")).intValue(),
                    ((Number) map.get("height")).intValue(),
                    ((Number) map.get("length")).intValue()
            ));
        }
        return index;
    }
}
