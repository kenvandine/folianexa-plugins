package io.github.phqen1x.worldeditcraft.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiniJsonTest {

    @Test
    void writesAndReadsBackAPlainObject() {
        Map<String, Object> value = Map.of("name", "hall", "count", 3.0, "ok", true);
        String json = MiniJson.write(value);
        Object readBack = MiniJson.parse(json);
        assertEquals(value, readBack);
    }

    @Test
    void writesIntegersWithoutADecimalPoint() {
        assertEquals("{\"n\":3}", MiniJson.write(Map.of("n", 3)));
    }

    @Test
    void strictModeRejectsTrailingComma() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":1,}"));
    }

    @Test
    void strictModeRejectsUnquotedKeys() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{a:1}"));
    }

    @Test
    void strictModeRejectsComments() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":1 // comment\n}"));
    }

    @Test
    void forgivingModeToleratesTrailingCommaInObject() {
        Object result = MiniJson.parseForgiving("{\"a\":1,\"b\":2,}");
        assertEquals(Map.of("a", 1.0, "b", 2.0), result);
    }

    @Test
    void forgivingModeToleratesTrailingCommaInArray() {
        Object result = MiniJson.parseForgiving("[1,2,3,]");
        assertEquals(List.of(1.0, 2.0, 3.0), result);
    }

    @Test
    void forgivingModeToleratesSingleQuotedStrings() {
        Object result = MiniJson.parseForgiving("{'name': 'ruined_forge_hall'}");
        assertEquals(Map.of("name", "ruined_forge_hall"), result);
    }

    @Test
    void forgivingModeToleratesUnquotedKeys() {
        Object result = MiniJson.parseForgiving("{name: \"hall\", seed_offset: 3}");
        assertEquals(Map.of("name", "hall", "seed_offset", 3.0), result);
    }

    @Test
    void forgivingModeToleratesLineComments() {
        Object result = MiniJson.parseForgiving("""
                {
                  // this is the structure's name
                  "name": "hall"
                }
                """);
        assertEquals(Map.of("name", "hall"), result);
    }

    @Test
    void forgivingModeStillParsesOrdinaryStrictJson() {
        Object result = MiniJson.parseForgiving("{\"a\":[1,2,3],\"b\":{\"c\":true}}");
        assertEquals(Map.of("a", List.of(1.0, 2.0, 3.0), "b", Map.of("c", true)), result);
    }
}
