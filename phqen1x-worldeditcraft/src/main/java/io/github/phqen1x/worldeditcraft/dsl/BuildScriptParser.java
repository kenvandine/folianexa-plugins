package io.github.phqen1x.worldeditcraft.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the plain {@code Map}/{@code List}/{@code String}/{@code Number}
 * tree a JSON parser hands back into a {@link BuildScript}. Only
 * structural problems are reported here (wrong shape, wrong type, a
 * missing required top-level field) — semantic checks (caps, palette
 * references, unknown ops) are {@link BuildScriptValidator}'s job.
 * Unknown op names are kept as ordinary {@link BuildOp}s rather than
 * dropped, so the validator can report exactly which invented name the
 * model used.
 */
public final class BuildScriptParser {

    private BuildScriptParser() {
    }

    public record ParseResult(BuildScript script, List<ValidationIssue> issues) {
    }

    @SuppressWarnings("unchecked")
    public static ParseResult parse(Object rawJson) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (!(rawJson instanceof Map<?, ?> root)) {
            issues.add(ValidationIssue.fatal(-1, "The response was not a JSON object.",
                    "Your response must be a single JSON object, not an array or scalar value."));
            return new ParseResult(emptyScript(), issues);
        }
        Map<String, Object> map = (Map<String, Object>) root;

        String name = stringOrNull(map.get("name"));
        if (name == null || name.isBlank()) {
            issues.add(ValidationIssue.fatal(-1, "Missing required field 'name'.",
                    "Your JSON object must include a string 'name' field."));
            name = "unnamed";
        }

        int[] size = parseSize(map.get("size"), issues);

        long seed = map.get("seed") instanceof Number number ? number.longValue() : name.hashCode();

        Map<String, String> palette = parsePalette(map.get("palette"), issues);

        List<BuildOp> ops = parseOps(map.get("ops"), issues);

        String description = stringOrNull(map.get("description"));
        description = description == null ? "" : description;

        return new ParseResult(new BuildScript(name, size, seed, palette, ops, description), issues);
    }

    private static int[] parseSize(Object raw, List<ValidationIssue> issues) {
        if (raw instanceof List<?> list && list.size() == 3
                && list.get(0) instanceof Number w && list.get(1) instanceof Number h && list.get(2) instanceof Number l) {
            return new int[]{w.intValue(), h.intValue(), l.intValue()};
        }
        issues.add(ValidationIssue.fatal(-1, "Missing or malformed required field 'size'.",
                "Your JSON object must include a 'size' field: a 3-element array [width, height, length]."));
        return new int[]{1, 1, 1};
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parsePalette(Object raw, List<ValidationIssue> issues) {
        if (!(raw instanceof Map<?, ?> map)) {
            issues.add(ValidationIssue.fatal(-1, "Missing or malformed required field 'palette'.",
                    "Your JSON object must include a 'palette' object mapping short keys to block-state strings."));
            return Map.of();
        }
        Map<String, String> palette = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            palette.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return palette;
    }

    @SuppressWarnings("unchecked")
    private static List<BuildOp> parseOps(Object raw, List<ValidationIssue> issues) {
        if (!(raw instanceof List<?> list)) {
            issues.add(ValidationIssue.fatal(-1, "Missing or malformed required field 'ops'.",
                    "Your JSON object must include an 'ops' array."));
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (!(entry instanceof Map<?, ?> opMap)) {
                issues.add(ValidationIssue.error(i, "Operation " + i + " is not a JSON object.",
                        "Operation " + i + " must be a JSON object with an 'op' field. It was dropped."));
                continue;
            }
            Object opName = opMap.get("op");
            if (!(opName instanceof String name) || name.isBlank()) {
                issues.add(ValidationIssue.error(i, "Operation " + i + " is missing its 'op' field.",
                        "Operation " + i + " must include a string 'op' field naming the operation. It was dropped."));
                continue;
            }
            Map<String, Object> fields = new LinkedHashMap<>((Map<String, Object>) opMap);
            fields.remove("op");
            ops.add(new BuildOp(name, fields, i));
        }
        return ops;
    }

    private static String stringOrNull(Object value) {
        return value instanceof String s ? s : null;
    }

    private static BuildScript emptyScript() {
        return new BuildScript("unnamed", new int[]{1, 1, 1}, 0, Map.of(), List.of(), "");
    }
}
