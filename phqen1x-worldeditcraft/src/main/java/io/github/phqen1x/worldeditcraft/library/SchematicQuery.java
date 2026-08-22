package io.github.phqen1x.worldeditcraft.library;

/**
 * A filter/sort/page request against a {@link SchematicIndex} — backs
 * {@code /wec list [page] [--tag t] [--search s]}. Results are always
 * sorted newest-first; {@code page} is 1-based.
 */
public record SchematicQuery(String search, String tag, int page, int pageSize) {

    public static SchematicQuery firstPage() {
        return new SchematicQuery(null, null, 1, 10);
    }

    public SchematicQuery {
        page = Math.max(1, page);
        pageSize = Math.max(1, pageSize);
    }
}
