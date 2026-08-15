package io.github.kenvandine.folianexastats;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniJsonTest {

    @Test
    void writesAndParsesRoundTrip() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", "abc-123");
        body.put("kills", 5.0);
        body.put("nested", Map.of("a", 1.0));
        body.put("list", List.of(1.0, 2.0, 3.0));
        body.put("nothing", null);
        body.put("flag", true);

        String json = MiniJson.write(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) MiniJson.parse(json);

        assertEquals("abc-123", parsed.get("uuid"));
        assertEquals(5.0, ((Number) parsed.get("kills")).doubleValue());
        assertNull(parsed.get("nothing"));
        assertEquals(Boolean.TRUE, parsed.get("flag"));
    }

    @Test
    void integerValuedDoublesWriteWithoutDecimalPoint() {
        String json = MiniJson.write(Map.of("kills", 5.0));
        assertTrue(json.contains("\"kills\":5"), json);
        assertTrue(!json.contains("5.0"), json);
    }

    @Test
    void fractionalValuesWritePreserved() {
        String json = MiniJson.write(Map.of("wealth", 1000.5));
        assertTrue(json.contains("1000.5"), json);
    }

    @Test
    void escapesSpecialCharactersInStrings() {
        String json = MiniJson.write(Map.of("name", "quote\"back\\slash\nnewline"));
        Object parsed = MiniJson.parse(json);
        @SuppressWarnings("unchecked")
        String value = (String) ((Map<String, Object>) parsed).get("name");
        assertEquals("quote\"back\\slash\nnewline", value);
    }

    @Test
    void parsesRealMgmtPlayerProfileResponseShape() {
        String json = """
                {"uuid":"abc","username":"Steve","first_seen":"2026-08-15T00:00:00","last_seen":"2026-08-15T00:00:00",
                 "stats":{"kills":5,"deaths":1.5},"playtime_daily":[{"date":"2026-08-15","seconds":600}]}
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) MiniJson.parse(json);
        assertEquals("Steve", parsed.get("username"));
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) parsed.get("stats");
        assertEquals(5.0, ((Number) stats.get("kills")).doubleValue());
        @SuppressWarnings("unchecked")
        List<Object> daily = (List<Object>) parsed.get("playtime_daily");
        assertEquals(1, daily.size());
    }

    @Test
    void parsesEmptyObjectAndArray() {
        assertEquals(Map.of(), MiniJson.parse("{}"));
        assertEquals(List.of(), MiniJson.parse("[]"));
    }

    @Test
    void rejectsTrailingContent() {
        try {
            MiniJson.parse("{}garbage");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
