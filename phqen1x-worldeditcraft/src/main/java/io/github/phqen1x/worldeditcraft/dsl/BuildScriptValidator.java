package io.github.phqen1x.worldeditcraft.dsl;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Semantic checks on a parsed {@link BuildScript}: caps, op counts,
 * palette/block references, and coordinate bounds. Never throws; always
 * returns the full set of issues found (a repair round that fixed one
 * problem at a time would need ten rounds — see the design doc's
 * "Validation" section for the severity table this mirrors).
 *
 * <p>{@code block-policy: vanilla}'s check here is syntax plus namespace
 * only (must parse as a block-state string, namespace {@code minecraft})
 * — not membership in the real vanilla block registry, which would need
 * a full ID list this implementation doesn't yet carry. That's a known,
 * documented gap (see the plugin README), not an oversight.
 */
public final class BuildScriptValidator {

    public record GenerationLimits(
            int maxDimension,
            long maxVolume,
            int maxOps,
            String blockPolicy,
            List<String> blockAllowlist
    ) {
        public GenerationLimits {
            blockAllowlist = List.copyOf(blockAllowlist);
        }
    }

    public record ValidationResult(List<ValidationIssue> issues, int[] effectiveSize, List<BuildOp> executableOps) {
        public ValidationResult {
            issues = List.copyOf(issues);
            effectiveSize = effectiveSize.clone();
            executableOps = List.copyOf(executableOps);
        }

        @Override
        public int[] effectiveSize() {
            return effectiveSize.clone();
        }

        public boolean shouldRepair() {
            return issues.stream().anyMatch(ValidationIssue::triggersRepair);
        }
    }

    private static final int MAX_PLACE_BLOCK_OPS = 200;
    private static final int MAX_REPEAT_COUNT = 64;

    private BuildScriptValidator() {
    }

    public static ValidationResult validate(BuildScript script, GenerationLimits limits) {
        List<ValidationIssue> issues = new ArrayList<>();
        int[] effectiveSize = validateSize(script.size(), limits, issues);

        List<BuildOp> ops = script.ops();
        if (ops.size() > limits.maxOps()) {
            issues.add(ValidationIssue.fatal(-1,
                    "Script has " + ops.size() + " operations, exceeding the limit of " + limits.maxOps() + ".",
                    "Your ops array has " + ops.size() + " entries. The maximum is " + limits.maxOps()
                            + ". Use fewer, bulkier operations and 'repeat' instead of repeating yourself."));
            ops = ops.subList(0, limits.maxOps());
        }

        List<BuildOp> executable = new ArrayList<>();
        int placeBlockCount = 0;
        for (BuildOp op : ops) {
            BuildOp validated = validateOp(op, script.palette(), limits, effectiveSize, issues);
            if (validated == null) {
                continue;
            }
            executable.add(validated);
            if (validated.op().equals("place_block")) {
                placeBlockCount++;
            }
        }

        if (placeBlockCount > MAX_PLACE_BLOCK_OPS) {
            issues.add(ValidationIssue.warning(-1,
                    "Script uses " + placeBlockCount + " individual place_block operations.",
                    "You used " + placeBlockCount + " place_block operations, over the recommended limit of "
                            + MAX_PLACE_BLOCK_OPS + ". Prefer bulk operations (box, scatter, noise_replace) for repeated detail."));
        }

        if (executable.isEmpty() && !script.ops().isEmpty()) {
            issues.add(ValidationIssue.fatal(-1, "No operations survived validation.",
                    "None of your operations were valid. Review the errors above and return a corrected complete script."));
        }

        return new ValidationResult(issues, effectiveSize, executable);
    }

    /** Checked after rasterization — a script that produces no blocks at all is the classic silent failure. */
    public static Optional<ValidationIssue> checkNotEmpty(VoxelGrid grid) {
        for (int y = 0; y < grid.height(); y++) {
            for (int z = 0; z < grid.length(); z++) {
                for (int x = 0; x < grid.width(); x++) {
                    if (!grid.isAir(x, y, z)) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.of(ValidationIssue.fatal(-1, "The generated structure is entirely empty (all air).",
                "Your script produced no blocks at all. Make sure at least one op actually places a non-air block."));
    }

    private static int[] validateSize(int[] size, GenerationLimits limits, List<ValidationIssue> issues) {
        boolean overCap = size[0] > limits.maxDimension() || size[1] > limits.maxDimension() || size[2] > limits.maxDimension();
        long volume = (long) size[0] * size[1] * size[2];
        if (overCap || volume > limits.maxVolume() || size[0] < 1 || size[1] < 1 || size[2] < 1) {
            issues.add(ValidationIssue.fatal(-1,
                    "size " + java.util.Arrays.toString(size) + " exceeds the limits (max dimension "
                            + limits.maxDimension() + ", max volume " + limits.maxVolume() + ").",
                    "size is " + java.util.Arrays.toString(size) + ". The maximum for any axis is "
                            + limits.maxDimension() + " and the maximum volume (width*height*length) is "
                            + limits.maxVolume() + ". Return a corrected size."));
        }

        int[] clamped = {
                Math.max(1, Math.min(size[0], limits.maxDimension())),
                Math.max(1, Math.min(size[1], limits.maxDimension())),
                Math.max(1, Math.min(size[2], limits.maxDimension()))
        };
        while ((long) clamped[0] * clamped[1] * clamped[2] > limits.maxVolume()) {
            int largestAxis = largestAxisIndex(clamped);
            if (clamped[largestAxis] <= 1) {
                break; // already as small as possible; nothing more to shrink
            }
            clamped[largestAxis]--;
        }
        return clamped;
    }

    private static int largestAxisIndex(int[] size) {
        int index = 0;
        for (int i = 1; i < size.length; i++) {
            if (size[i] > size[index]) {
                index = i;
            }
        }
        return index;
    }

    private static BuildOp validateOp(BuildOp op, Map<String, String> palette, GenerationLimits limits, int[] size, List<ValidationIssue> issues) {
        OpRegistry.OpSpec spec = OpRegistry.get(op.op());
        if (spec == null) {
            issues.add(ValidationIssue.error(op.sourceIndex(),
                    "Operation " + op.sourceIndex() + " uses unknown op '" + op.op() + "'.",
                    "Operation " + op.sourceIndex() + " uses '" + op.op() + "', which is not a valid op name. "
                            + "Valid op names are: " + String.join(", ", OpRegistry.knownNames()) + ". This operation was dropped."));
            return null;
        }

        for (String required : spec.requiredFields()) {
            if (!op.has(required)) {
                issues.add(ValidationIssue.error(op.sourceIndex(),
                        "Operation " + op.sourceIndex() + " (" + op.op() + ") is missing required field '" + required + "'.",
                        "Operation " + op.sourceIndex() + " (" + op.op() + ") is missing its required '" + required
                                + "' field. This operation was dropped — include all required fields."));
                return null;
            }
        }

        for (String blockField : List.of("block", "find")) {
            if (op.has(blockField) && !validateBlockReference(op.fieldString(blockField), palette, limits, op.sourceIndex(), blockField, issues)) {
                return null;
            }
        }

        for (String tripleField : List.of("from", "to", "at")) {
            if (op.has(tripleField) && op.fieldIntTriple(tripleField) == null) {
                issues.add(ValidationIssue.error(op.sourceIndex(),
                        "Operation " + op.sourceIndex() + " (" + op.op() + ")'s '" + tripleField + "' is not a valid [x, y, z] triple.",
                        "Operation " + op.sourceIndex() + " (" + op.op() + ")'s '" + tripleField
                                + "' must be a `[x, y, z]` array of three numbers. This operation was dropped — fix its shape."));
                return null;
            }
        }
        if (op.has("region") && op.fieldRegion("region") == null) {
            issues.add(ValidationIssue.error(op.sourceIndex(),
                    "Operation " + op.sourceIndex() + " (" + op.op() + ")'s 'region' is not a valid [[x,y,z],[x,y,z]] pair.",
                    "Operation " + op.sourceIndex() + " (" + op.op() + ")'s 'region' must be a `[[x,y,z], [x,y,z]]` "
                            + "array of two coordinate triples. This operation was dropped — fix its shape."));
            return null;
        }

        int repeatCount = op.repeatCount();
        Map<String, Object> fields = new LinkedHashMap<>(op.fields());
        if (repeatCount > MAX_REPEAT_COUNT) {
            issues.add(ValidationIssue.warning(op.sourceIndex(),
                    "Operation " + op.sourceIndex() + "'s repeat.count (" + repeatCount + ") was clamped to " + MAX_REPEAT_COUNT + ".",
                    "Operation " + op.sourceIndex() + "'s repeat.count exceeded the maximum of " + MAX_REPEAT_COUNT + " and was clamped."));
            Object repeat = fields.get("repeat");
            if (repeat instanceof Map<?, ?> repeatMap) {
                Map<String, Object> newRepeat = new LinkedHashMap<>(repeatMap.size());
                repeatMap.forEach((k, v) -> newRepeat.put(String.valueOf(k), v));
                newRepeat.put("count", MAX_REPEAT_COUNT);
                fields.put("repeat", newRepeat);
            }
        }

        boolean[] anyClamped = {false};
        clampCoordinateField(fields, "from", size, anyClamped);
        clampCoordinateField(fields, "to", size, anyClamped);
        clampCoordinateField(fields, "at", size, anyClamped);
        clampRegionField(fields, "region", size, anyClamped);
        if (anyClamped[0]) {
            issues.add(ValidationIssue.warning(op.sourceIndex(),
                    "Operation " + op.sourceIndex() + " had coordinates outside the schematic bounds; they were clamped.",
                    "Operation " + op.sourceIndex() + " had a coordinate outside the schematic's bounds (size "
                            + java.util.Arrays.toString(size) + "). It was clamped in place."));
        }

        return new BuildOp(op.op(), fields, op.sourceIndex());
    }

    private static boolean validateBlockReference(String value, Map<String, String> palette, GenerationLimits limits,
                                                   int opIndex, String fieldName, List<ValidationIssue> issues) {
        if (palette.containsKey(value)) {
            return true;
        }
        BlockStateRef ref;
        try {
            ref = BlockStateRef.parse(value);
        } catch (IllegalArgumentException e) {
            issues.add(ValidationIssue.error(opIndex,
                    "Operation " + opIndex + "'s '" + fieldName + "' ('" + value + "') is not a defined palette key or a valid block-state string.",
                    "Operation " + opIndex + " uses '" + fieldName + "': '" + value + "', which is not one of the defined "
                            + "palette keys (" + String.join(", ", palette.keySet()) + ") and does not parse as a block-state string. This operation was dropped."));
            return false;
        }

        return switch (limits.blockPolicy()) {
            case "any" -> true;
            case "allowlist" -> {
                if (limits.blockAllowlist().contains(ref.toString()) || limits.blockAllowlist().contains(ref.namespace() + ":" + ref.id())) {
                    yield true;
                }
                issues.add(ValidationIssue.error(opIndex,
                        "Operation " + opIndex + "'s block '" + ref + "' is not on the configured allowlist.",
                        "Operation " + opIndex + " uses block '" + ref + "', which is not on this server's allowed block list. This operation was dropped."));
                yield false;
            }
            default -> { // "vanilla"
                if (!ref.namespace().equals("minecraft")) {
                    issues.add(ValidationIssue.error(opIndex,
                            "Operation " + opIndex + "'s block '" + ref + "' is not a vanilla Minecraft block.",
                            "Operation " + opIndex + " uses block '" + ref + "', which is not in the vanilla Minecraft namespace. "
                                    + "Use a real minecraft: block ID. This operation was dropped."));
                    yield false;
                }
                yield true;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static void clampCoordinateField(Map<String, Object> fields, String key, int[] size, boolean[] anyClamped) {
        if (fields.get(key) instanceof List<?> list && list.size() == 3) {
            fields.put(key, clampTriple((List<Object>) list, size, anyClamped));
        }
    }

    @SuppressWarnings("unchecked")
    private static void clampRegionField(Map<String, Object> fields, String key, int[] size, boolean[] anyClamped) {
        if (fields.get(key) instanceof List<?> list && list.size() == 2) {
            List<Object> result = new ArrayList<>(2);
            for (Object corner : list) {
                if (corner instanceof List<?> triple && triple.size() == 3) {
                    result.add(clampTriple((List<Object>) triple, size, anyClamped));
                } else {
                    result.add(corner);
                }
            }
            fields.put(key, result);
        }
    }

    private static List<Object> clampTriple(List<Object> triple, int[] size, boolean[] anyClamped) {
        List<Object> result = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            int value = triple.get(i) instanceof Number number ? number.intValue() : 0;
            int clamped = Math.max(0, Math.min(value, size[i] - 1));
            if (clamped != value) {
                anyClamped[0] = true;
            }
            result.add(clamped);
        }
        return result;
    }
}
