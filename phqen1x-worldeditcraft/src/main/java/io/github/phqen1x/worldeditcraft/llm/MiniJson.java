package io.github.phqen1x.worldeditcraft.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, self-contained JSON reader/writer, ported from {@code
 * folianexa-stats}'s {@code MiniJson} (same rationale: nothing here to
 * shade or risk a classpath conflict with another plugin's JSON
 * library) and extended with a forgiving read mode for the specific
 * things a local LLM's "JSON" output actually contains: trailing
 * commas, single-quoted strings, unquoted object keys, and {@code //}
 * line comments. Strict-mode parsing of model output would send a
 * perfectly recoverable response back for another expensive inference
 * round (see docs/phqen1x-rpg-suite/04-lemonade-integration.md's
 * "Getting JSON out" §3). {@link #write} always produces strict,
 * standard JSON — forgiveness is a read-side-only concern.
 *
 * <p>Values are plain {@code Map<String, Object>}, {@code List<Object>},
 * {@code String}, {@code Double}, {@code Boolean}, or {@code null}.
 */
public final class MiniJson {

    private MiniJson() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Boolean b) {
            sb.append(b.toString());
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(entry.getKey()), sb);
                sb.append(':');
                writeValue(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                writeValue(item, sb);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass());
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Strict parse — rejects trailing commas, unquoted keys, comments, single quotes. */
    public static Object parse(String json) {
        return parse(json, false);
    }

    /** Forgiving parse — for model output. See the class docs for exactly what this tolerates. */
    public static Object parseForgiving(String json) {
        return parse(json, true);
    }

    private static Object parse(String json, boolean forgiving) {
        Parser parser = new Parser(json, forgiving);
        Object value = parser.parseValue();
        parser.skipWhitespaceAndComments();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Trailing content after JSON value at position " + parser.pos);
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private final boolean forgiving;
        private int pos;

        Parser(String s, boolean forgiving) {
            this.s = s;
            this.forgiving = forgiving;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespaceAndComments() {
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (forgiving && c == '/' && pos + 1 < s.length() && s.charAt(pos + 1) == '/') {
                    while (!atEnd() && s.charAt(pos) != '\n') pos++;
                } else {
                    break;
                }
            }
        }

        char peek() {
            if (atEnd()) throw new IllegalArgumentException("Unexpected end of JSON input");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (atEnd() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        Object parseValue() {
            skipWhitespaceAndComments();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"', '\'' -> parseString(c);
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespaceAndComments();
            if (!atEnd() && peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespaceAndComments();
                String key = parseKey();
                skipWhitespaceAndComments();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespaceAndComments();
                if (!atEnd() && peek() == ',') {
                    pos++;
                    skipWhitespaceAndComments();
                    if (forgiving && !atEnd() && peek() == '}') {
                        break; // trailing comma
                    }
                    continue;
                }
                break;
            }
            skipWhitespaceAndComments();
            expect('}');
            return result;
        }

        private String parseKey() {
            char c = peek();
            if (c == '"' || c == '\'') {
                return parseString(c);
            }
            if (!forgiving) {
                throw new IllegalArgumentException("Expected a quoted key at position " + pos);
            }
            int start = pos;
            while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '-')) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("Expected an object key at position " + pos);
            }
            return s.substring(start, pos);
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespaceAndComments();
            if (!atEnd() && peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                Object value = parseValue();
                result.add(value);
                skipWhitespaceAndComments();
                if (!atEnd() && peek() == ',') {
                    pos++;
                    skipWhitespaceAndComments();
                    if (forgiving && !atEnd() && peek() == ']') {
                        break; // trailing comma
                    }
                    continue;
                }
                break;
            }
            skipWhitespaceAndComments();
            expect(']');
            return result;
        }

        String parseString(char quote) {
            expect(quote);
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == quote) break;
                if (c == '\\') {
                    char esc = peek();
                    pos++;
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\'' -> sb.append('\'');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape '\\" + esc + "' at position " + pos);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid literal at position " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid literal at position " + pos);
        }

        Double parseNumber() {
            int start = pos;
            if (!atEnd() && peek() == '-') pos++;
            while (!atEnd() && Character.isDigit(peek())) pos++;
            if (!atEnd() && peek() == '.') {
                pos++;
                while (!atEnd() && Character.isDigit(peek())) pos++;
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                pos++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) pos++;
                while (!atEnd() && Character.isDigit(peek())) pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("Invalid number at position " + pos);
            }
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
