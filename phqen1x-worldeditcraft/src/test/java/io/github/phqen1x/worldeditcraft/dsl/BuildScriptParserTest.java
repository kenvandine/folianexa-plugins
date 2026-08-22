package io.github.phqen1x.worldeditcraft.dsl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildScriptParserTest {

    @Test
    void parsesAWellFormedScript() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "wayside_shrine");
        raw.put("size", List.of(9.0, 7.0, 9.0));
        raw.put("seed", 4412.0);
        raw.put("palette", Map.of("base", "minecraft:stone_bricks"));
        raw.put("ops", List.of(
                Map.of("op", "box", "from", List.of(0.0, 0.0, 0.0), "to", List.of(8.0, 0.0, 8.0), "block", "base"),
                Map.of("op", "marker", "at", List.of(4.0, 1.0, 4.0), "id", "entrance")
        ));

        BuildScriptParser.ParseResult result = BuildScriptParser.parse(raw);

        assertTrue(result.issues().isEmpty());
        assertEquals("wayside_shrine", result.script().name());
        assertEquals(4412L, result.script().seed());
        assertEquals(2, result.script().ops().size());
        assertEquals("box", result.script().ops().get(0).op());
        assertEquals("minecraft:stone_bricks", result.script().palette().get("base"));
    }

    @Test
    void everyRegisteredOpNameParsesWithoutBeingDropped() {
        for (String opName : OpRegistry.knownNames()) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("name", "test");
            raw.put("size", List.of(4.0, 4.0, 4.0));
            raw.put("palette", Map.of());
            raw.put("ops", List.of(Map.of("op", opName)));

            BuildScriptParser.ParseResult result = BuildScriptParser.parse(raw);
            assertEquals(1, result.script().ops().size(), "op '" + opName + "' should still parse structurally");
            assertEquals(opName, result.script().ops().get(0).op());
        }
    }

    @Test
    void unknownOpNamesAreCollectedNotThrown() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "test");
        raw.put("size", List.of(4.0, 4.0, 4.0));
        raw.put("palette", Map.of());
        raw.put("ops", List.of(Map.of("op", "levitate_everything")));

        BuildScriptParser.ParseResult result = BuildScriptParser.parse(raw);

        assertTrue(result.issues().isEmpty()); // structural parse alone doesn't reject unknown op names
        assertEquals(1, result.script().ops().size());
        assertEquals("levitate_everything", result.script().ops().get(0).op());
    }

    @Test
    void opWithoutOpFieldIsDroppedAndReported() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "test");
        raw.put("size", List.of(4.0, 4.0, 4.0));
        raw.put("palette", Map.of());
        raw.put("ops", List.of(Map.of("block", "minecraft:stone")));

        BuildScriptParser.ParseResult result = BuildScriptParser.parse(raw);

        assertFalse(result.issues().isEmpty());
        assertTrue(result.script().ops().isEmpty());
    }

    @Test
    void missingNameProducesFatalIssueButStillReturnsAScript() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("size", List.of(4.0, 4.0, 4.0));
        raw.put("palette", Map.of());
        raw.put("ops", List.of());

        BuildScriptParser.ParseResult result = BuildScriptParser.parse(raw);

        assertTrue(result.issues().stream().anyMatch(ValidationIssue::triggersRepair));
    }

    @Test
    void nonObjectTopLevelIsFatal() {
        BuildScriptParser.ParseResult result = BuildScriptParser.parse(List.of("not", "an", "object"));
        assertTrue(result.issues().stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.FATAL));
    }
}
