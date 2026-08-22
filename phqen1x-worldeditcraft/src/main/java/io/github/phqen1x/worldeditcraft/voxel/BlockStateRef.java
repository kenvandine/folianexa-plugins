package io.github.phqen1x.worldeditcraft.voxel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A parsed block-state string, e.g. {@code minecraft:oak_stairs[facing=north,half=top]}
 * — the same notation {@code Bukkit.createBlockData(String)} accepts.
 * Properties are stored sorted alphabetically so the same block state
 * always produces the same {@link #toString()}, which is what makes
 * schematic palettes stable and lets two runs of the same build script
 * hash identically (see the design doc's "Palette strings" section).
 */
public record BlockStateRef(String namespace, String id, Map<String, String> properties) {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    public BlockStateRef {
        properties = properties.isEmpty() ? Map.of() : new TreeMap<>(properties);
    }

    public static BlockStateRef parse(String raw) {
        String s = raw.strip();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Blank block-state string");
        }

        String namespace = DEFAULT_NAMESPACE;
        String rest = s;
        int colon = s.indexOf(':');
        int bracket = s.indexOf('[');
        if (colon >= 0 && (bracket < 0 || colon < bracket)) {
            namespace = s.substring(0, colon);
            rest = s.substring(colon + 1);
        }

        String id;
        Map<String, String> props = Map.of();
        int propsStart = rest.indexOf('[');
        if (propsStart < 0) {
            id = rest;
        } else {
            if (!rest.endsWith("]")) {
                throw new IllegalArgumentException("Malformed block-state string, missing closing ']': " + raw);
            }
            id = rest.substring(0, propsStart);
            String body = rest.substring(propsStart + 1, rest.length() - 1);
            props = parseProperties(body, raw);
        }

        if (id.isBlank()) {
            throw new IllegalArgumentException("Malformed block-state string, missing block id: " + raw);
        }
        return new BlockStateRef(namespace, id, props);
    }

    private static Map<String, String> parseProperties(String body, String raw) {
        if (body.isBlank()) {
            return Map.of();
        }
        Map<String, String> props = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Malformed block-state property '" + pair + "' in: " + raw);
            }
            String key = pair.substring(0, eq).strip();
            String value = pair.substring(eq + 1).strip();
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("Malformed block-state property '" + pair + "' in: " + raw);
            }
            props.put(key, value);
        }
        return props;
    }

    public BlockStateRef withProperty(String key, String value) {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(key, value);
        return new BlockStateRef(namespace, id, updated);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(namespace).append(':').append(id);
        if (!properties.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
