package io.github.phqen1x.worldeditcraft.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a JSON object out of whatever a model actually returned, since
 * {@code response_format} isn't documented as supported by Lemonade
 * Server (see docs/phqen1x-rpg-suite/04-lemonade-integration.md) and a
 * well-behaved-instruction assumption alone isn't load-bearing. Four
 * layers, cheapest first: strip a ```json fence if present, scan for the
 * first {@code {} and walk forward tracking brace depth while respecting
 * string literals and escapes (a naive brace count breaks on any
 * block-state string or prose containing a brace), fall back to the
 * last {@code {} in the response (the "here is my reasoning... now the
 * JSON" shape), then hand the extracted text to {@link MiniJson#parseForgiving}.
 */
public final class JsonCoercion {

    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private JsonCoercion() {
    }

    /** @throws IllegalArgumentException if no JSON object could be found or parsed anywhere in the response. */
    public static Object extractAndParse(String rawResponse) {
        String unfenced = stripFence(rawResponse);

        String fromFirst = extractBalancedObject(unfenced, unfenced.indexOf('{'));
        if (fromFirst != null) {
            try {
                return MiniJson.parseForgiving(fromFirst);
            } catch (IllegalArgumentException ignored) {
                // fall through to the last-'{' fallback below
            }
        }

        String fromLast = extractBalancedObject(unfenced, unfenced.lastIndexOf('{'));
        if (fromLast != null) {
            return MiniJson.parseForgiving(fromLast);
        }

        throw new IllegalArgumentException("No JSON object found in model response");
    }

    private static String stripFence(String raw) {
        Matcher matcher = FENCE.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return raw;
    }

    /**
     * Starting at {@code openBraceIndex} (must point at a {@code '{'}),
     * walks forward tracking brace depth while skipping over
     * double-quoted string literals (backslash-escaped) so a brace
     * inside a block-state string doesn't miscount. Deliberately does
     * <em>not</em> treat a bare {@code '} as a string delimiter here —
     * unlike {@link MiniJson#parseForgiving}, which tolerates
     * single-quoted JSON once a candidate object has already been
     * isolated, an apostrophe is far more likely to be ordinary English
     * prose ("isn't", "here's") surrounding the JSON than an intentional
     * string boundary, and misreading it as one would swallow real
     * braces. Returns the balanced {@code {...}} substring, or {@code
     * null} if the braces never balance before the text ends.
     */
    private static String extractBalancedObject(String text, int openBraceIndex) {
        if (openBraceIndex < 0 || text.charAt(openBraceIndex) != '{') {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = openBraceIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++; // skip the escaped character, whatever it is
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(openBraceIndex, i + 1);
                }
            }
        }
        return null;
    }
}
