package io.github.phqen1x.worldeditcraft.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonCoercionTest {

    @Test
    void extractsFromAFencedCodeBlock() {
        String response = """
                Sure, here's the structure:
                ```json
                {"name": "hall", "size": [1, 2, 3]}
                ```
                Let me know if you'd like changes!""";
        Object result = JsonCoercion.extractAndParse(response);
        assertEquals("hall", ((Map<?, ?>) result).get("name"));
    }

    @Test
    void extractsFromProseThenJson() {
        String response = "Here is my reasoning about the structure. " +
                "Now here is the final JSON object: {\"name\": \"hall\"}";
        Object result = JsonCoercion.extractAndParse(response);
        assertEquals("hall", ((Map<?, ?>) result).get("name"));
    }

    @Test
    void extractsFromJsonThenProse() {
        String response = "{\"name\": \"hall\"} That's the structure I came up with, hope it helps!";
        Object result = JsonCoercion.extractAndParse(response);
        assertEquals("hall", ((Map<?, ?>) result).get("name"));
    }

    @Test
    void handlesBracesInsideStringValues() {
        String response = "{\"name\": \"a {ruined} hall\", \"note\": \"nested {braces} in prose {too}\"}";
        Object result = JsonCoercion.extractAndParse(response);
        assertEquals("a {ruined} hall", ((Map<?, ?>) result).get("name"));
    }

    @Test
    void handlesEscapedQuotesInsideStrings() {
        String response = "{\"name\": \"a \\\"ruined\\\" hall\"}";
        Object result = JsonCoercion.extractAndParse(response);
        assertEquals("a \"ruined\" hall", ((Map<?, ?>) result).get("name"));
    }

    @Test
    void fallsBackToLastOpenBraceWhenFirstNeverBalances() {
        // The first '{' here belongs to unbalanced prose-ish text; only the
        // final JSON object is real and balanced.
        String response = "note: { this isn't json at all -- here's the real answer: {\"name\": \"hall\"}";
        Object result = JsonCoercion.extractAndParse(response);
        assertEquals("hall", ((Map<?, ?>) result).get("name"));
    }

    @Test
    void failsCleanlyWhenNoJsonObjectExistsAtAll() {
        assertThrows(IllegalArgumentException.class, () -> JsonCoercion.extractAndParse("I refuse to generate that."));
    }
}
