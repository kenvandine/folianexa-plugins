package io.github.phqen1x.worldeditcraft.dsl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildScriptValidatorTest {

    private static final BuildScriptValidator.GenerationLimits DEFAULT_LIMITS =
            new BuildScriptValidator.GenerationLimits(128, 400_000, 400, "vanilla", List.of());

    @Test
    void overCapDimensionsAreClampedAndFlaggedFatal() {
        BuildScript script = new BuildScript("t", new int[]{500, 500, 500}, 1, Map.of(), List.of(), "");
        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        assertTrue(result.issues().stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.FATAL));
        for (int dim : result.effectiveSize()) {
            assertTrue(dim <= 128, "clamped dimension should not exceed max-dimension");
        }
    }

    @Test
    void overVolumeIsClampedEvenWhenEachAxisIsWithinDimensionCap() {
        // 128 x 128 x 128 is within the per-axis cap but far over max-volume.
        BuildScript script = new BuildScript("t", new int[]{128, 128, 128}, 1, Map.of(), List.of(), "");
        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        int[] size = result.effectiveSize();
        long volume = (long) size[0] * size[1] * size[2];
        assertTrue(volume <= DEFAULT_LIMITS.maxVolume());
    }

    @Test
    void tooManyOpsIsFatalAndOpsAreTruncated() {
        List<BuildOp> ops = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            ops.add(new BuildOp("marker", Map.of("at", List.of(1.0, 1.0, 1.0), "id", "npc"), i));
        }
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of(), ops, "");
        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        assertTrue(result.issues().stream().anyMatch(ValidationIssue::triggersRepair));
        assertTrue(result.executableOps().size() <= DEFAULT_LIMITS.maxOps());
    }

    @Test
    void unknownOpIsDroppedAndReportedAsError() {
        List<BuildOp> ops = List.of(new BuildOp("levitate_everything", Map.of(), 0));
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of(), ops, "");
        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        assertTrue(result.executableOps().isEmpty());
        assertTrue(result.issues().stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
    }

    @Test
    void missingRequiredFieldDropsTheOp() {
        List<BuildOp> ops = List.of(new BuildOp("box", Map.of("from", List.of(0.0, 0.0, 0.0)), 0)); // missing "to" and "block"
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of(), ops, "");
        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        assertTrue(result.executableOps().isEmpty());
        assertTrue(result.issues().stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
    }

    @Test
    void unknownPaletteKeyThatAlsoFailsToParseIsAnErrorListingDefinedKeys() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("from", List.of(0.0, 0.0, 0.0));
        fields.put("to", List.of(1.0, 1.0, 1.0));
        // Not a defined palette key, and malformed as a literal block-state
        // string too (unclosed property bracket) — the case the validator
        // can actually detect without a full vanilla block registry (see
        // BuildScriptValidator's class docs for that documented gap).
        fields.put("block", "collumn[facing=north");
        List<BuildOp> ops = List.of(new BuildOp("box", fields, 0));
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of("wall", "minecraft:stone"), ops, "");

        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        assertTrue(result.executableOps().isEmpty());
        int index = findIssueIndexMentioning(result.issues(), "collumn");
        assertTrue(index >= 0, "expected an issue mentioning the bad block reference 'collumn[facing=north'");
        assertTrue(result.issues().get(index).modelMessage().contains("wall"));
    }

    @Test
    void nonVanillaBlockIsRejectedUnderVanillaPolicy() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", List.of(0.0, 0.0, 0.0));
        fields.put("block", "modded:dwarven_forge_block");
        List<BuildOp> ops = List.of(new BuildOp("place_block", fields, 0));
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of(), ops, "");

        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);
        assertTrue(result.executableOps().isEmpty());
    }

    @Test
    void nonVanillaBlockIsAcceptedUnderAnyPolicy() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", List.of(0.0, 0.0, 0.0));
        fields.put("block", "modded:dwarven_forge_block");
        List<BuildOp> ops = List.of(new BuildOp("place_block", fields, 0));
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of(), ops, "");
        BuildScriptValidator.GenerationLimits anyPolicy =
                new BuildScriptValidator.GenerationLimits(128, 400_000, 400, "any", List.of());

        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, anyPolicy);
        assertEquals(1, result.executableOps().size());
    }

    @Test
    void outOfBoundsCoordinatesAreClampedAsWarningNotFatal() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", List.of(999.0, 1.0, 1.0));
        fields.put("block", "minecraft:stone");
        List<BuildOp> ops = List.of(new BuildOp("place_block", fields, 0));
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of(), ops, "");

        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        assertEquals(1, result.executableOps().size());
        assertFalse(result.issues().stream().anyMatch(ValidationIssue::triggersRepair));
        int[] clampedAt = result.executableOps().get(0).fieldIntTriple("at");
        assertEquals(9, clampedAt[0]); // clamped to size-1
    }

    @Test
    void repeatCountOverMaximumIsClampedAsWarning() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", List.of(0.0, 0.0, 0.0));
        fields.put("height", 1.0);
        fields.put("block", "minecraft:stone");
        fields.put("repeat", Map.of("count", 999.0, "step", List.of(1.0, 0.0, 0.0)));
        List<BuildOp> ops = List.of(new BuildOp("column", fields, 0));
        BuildScript script = new BuildScript("t", new int[]{500, 10, 10}, 1, Map.of(), ops, "");
        BuildScriptValidator.GenerationLimits wideLimits =
                new BuildScriptValidator.GenerationLimits(500, 10_000_000, 400, "vanilla", List.of());

        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, wideLimits);

        assertEquals(1, result.executableOps().size());
        assertFalse(result.issues().stream().anyMatch(ValidationIssue::triggersRepair));
        assertEquals(64, result.executableOps().get(0).repeatCount());
    }

    @Test
    void emptyOpsListAfterAllDroppedIsFatal() {
        List<BuildOp> ops = List.of(new BuildOp("unknown_op", Map.of(), 0));
        BuildScript script = new BuildScript("t", new int[]{10, 10, 10}, 1, Map.of(), ops, "");
        BuildScriptValidator.ValidationResult result = BuildScriptValidator.validate(script, DEFAULT_LIMITS);

        assertTrue(result.executableOps().isEmpty());
        assertTrue(result.issues().stream().anyMatch(ValidationIssue::triggersRepair));
    }

    private static int findIssueIndexMentioning(List<ValidationIssue> issues, String text) {
        for (int i = 0; i < issues.size(); i++) {
            if (issues.get(i).modelMessage().contains(text)) {
                return i;
            }
        }
        return -1;
    }
}
