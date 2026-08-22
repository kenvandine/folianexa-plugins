package io.github.phqen1x.worldeditcraft.dsl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed operation from a build script's {@code ops} array: its
 * {@code op} name, its position in the original array (for error
 * messages), and its fields as a plain {@code Map} — the shape {@code
 * MiniJson} hands back. Shared modifiers ({@code repeat}, {@code
 * replace_only}, {@code skip_air}, {@code chance}) are read out through
 * dedicated accessors since every op may carry them (see the design
 * doc's "Shared modifiers"); op-specific fields ({@code from}, {@code
 * to}, {@code block}, ...) are read through the generic {@code field*}
 * helpers by {@link OpRegistry}'s executors.
 *
 * <p>This is a single generic wrapper rather than one record per
 * operation (the design doc's suggested class layout) — a deliberate
 * simplification for the first implementation pass, trading the
 * compile-time field safety of ~20 separate op records for a single
 * small, uniform type that every {@link OpRegistry} entry can validate
 * and execute against. The wire contract (field names, JSON shape) is
 * unchanged either way.
 */
public final class BuildOp {

    private final String op;
    private final Map<String, Object> fields;
    private final int sourceIndex;

    public BuildOp(String op, Map<String, Object> fields, int sourceIndex) {
        this.op = op;
        this.fields = fields;
        this.sourceIndex = sourceIndex;
    }

    public String op() {
        return op;
    }

    public int sourceIndex() {
        return sourceIndex;
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public boolean has(String key) {
        return fields.containsKey(key);
    }

    public String fieldString(String key) {
        Object value = fields.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public String fieldString(String key, String fallback) {
        String value = fieldString(key);
        return value == null ? fallback : value;
    }

    public double fieldDouble(String key, double fallback) {
        Object value = fields.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public int fieldInt(String key, int fallback) {
        return (int) Math.round(fieldDouble(key, fallback));
    }

    public boolean fieldBoolean(String key, boolean fallback) {
        Object value = fields.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    /** A `[x, y, z]` integer triple field, e.g. {@code from}/{@code to}/{@code at}. */
    public int[] fieldIntTriple(String key) {
        Object value = fields.get(key);
        if (!(value instanceof List<?> list) || list.size() != 3) {
            return null;
        }
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.get(i) instanceof Number number)) {
                return null;
            }
            result[i] = (int) Math.round(number.doubleValue());
        }
        return result;
    }

    /** A `[[x1,y1,z1],[x2,y2,z2]]` region field, as {@code {from, to}}. */
    public int[][] fieldRegion(String key) {
        Object value = fields.get(key);
        if (!(value instanceof List<?> list) || list.size() != 2) {
            return null;
        }
        int[] from = tripleFrom(list.get(0));
        int[] to = tripleFrom(list.get(1));
        if (from == null || to == null) {
            return null;
        }
        return new int[][]{from, to};
    }

    private static int[] tripleFrom(Object value) {
        if (!(value instanceof List<?> list) || list.size() != 3) {
            return null;
        }
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.get(i) instanceof Number number)) {
                return null;
            }
            result[i] = (int) Math.round(number.doubleValue());
        }
        return result;
    }

    // --- Shared modifiers (see the design doc's "Shared modifiers") ---

    public int repeatCount() {
        Object repeat = fields.get("repeat");
        if (!(repeat instanceof Map<?, ?> map)) {
            return 1;
        }
        Object count = map.get("count");
        return count instanceof Number number ? Math.max(1, number.intValue()) : 1;
    }

    public int[] repeatStep() {
        Object repeat = fields.get("repeat");
        if (!(repeat instanceof Map<?, ?> map)) {
            return new int[]{0, 0, 0};
        }
        Object step = map.get("step");
        if (!(step instanceof List<?> list) || list.size() != 3) {
            return new int[]{0, 0, 0};
        }
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = list.get(i) instanceof Number number ? number.intValue() : 0;
        }
        return result;
    }

    public String replaceOnly() {
        return fieldString("replace_only");
    }

    public boolean skipAir() {
        return fieldBoolean("skip_air", false);
    }

    /** {@code null} means "always write" (no chance modifier present). */
    public Double chance() {
        Object value = fields.get("chance");
        return value instanceof Number number ? number.doubleValue() : null;
    }

    public int seedOffset() {
        return fieldInt("seed_offset", 0);
    }

    /**
     * A copy of this op with every coordinate-bearing field ({@code
     * from}/{@code to}/{@code at}/{@code region}) shifted by {@code
     * (dx, dy, dz)} — how {@code repeat} applies the same op {@code
     * count} times at successive offsets without the interpreter needing
     * to know each op's specific field shape beyond "these are the
     * coordinate fields every op may have".
     */
    public BuildOp translated(int dx, int dy, int dz) {
        Map<String, Object> shifted = new LinkedHashMap<>(fields);
        shiftTriple(shifted, "from", dx, dy, dz);
        shiftTriple(shifted, "to", dx, dy, dz);
        shiftTriple(shifted, "at", dx, dy, dz);
        shiftRegion(shifted, "region", dx, dy, dz);
        return new BuildOp(op, shifted, sourceIndex);
    }

    private static void shiftTriple(Map<String, Object> fields, String key, int dx, int dy, int dz) {
        if (fields.get(key) instanceof List<?> list && list.size() == 3) {
            fields.put(key, shiftedTripleValues(list, dx, dy, dz));
        }
    }

    private static void shiftRegion(Map<String, Object> fields, String key, int dx, int dy, int dz) {
        if (fields.get(key) instanceof List<?> list && list.size() == 2) {
            List<Object> shifted = List.of(
                    shiftedTripleOrOriginal(list.get(0), dx, dy, dz),
                    shiftedTripleOrOriginal(list.get(1), dx, dy, dz)
            );
            fields.put(key, shifted);
        }
    }

    private static Object shiftedTripleOrOriginal(Object value, int dx, int dy, int dz) {
        return value instanceof List<?> triple && triple.size() == 3
                ? shiftedTripleValues(triple, dx, dy, dz)
                : value;
    }

    private static List<Object> shiftedTripleValues(List<?> triple, int dx, int dy, int dz) {
        int[] deltas = {dx, dy, dz};
        List<Object> result = new java.util.ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            double value = triple.get(i) instanceof Number number ? number.doubleValue() : 0;
            result.add(value + deltas[i]);
        }
        return result;
    }
}
