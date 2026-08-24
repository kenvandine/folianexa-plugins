package io.github.kenvandine.flowerwatch.coreprotect;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Best-effort bridge to CoreProtect's public API, reached entirely
 * through reflection rather than a compile-time Gradle dependency.
 *
 * <p>Why reflection instead of a {@code compileOnly} dependency like
 * every other soft integration in this monorepo (PacketEvents/
 * PlaceholderAPI in Solstice): CoreProtect-CE — the fork this cluster
 * runs, {@code catalog.yaml}'s {@code CoreProtect} entry,
 * {@code github.com/PlayPro/CoreProtect} — has no working JitPack
 * build (every tagged version and recent commit under
 * {@code jitpack.io/api/builds/com.github.PlayPro/CoreProtect} reports
 * {@code "Error"}, checked when this plugin was written), and it isn't
 * published to Maven Central or PaperMC's own repository either. There
 * is no real Maven coordinate to depend on.
 *
 * <p>Reflection against its documented {@code net.coreprotect.CoreProtectAPI}
 * class is the same consumption pattern CoreProtect's own wiki has
 * recommended to third-party plugins for years (get the CoreProtect
 * plugin instance, call {@code getAPI()}, use the returned object) —
 * and it means a missing or API-incompatible CoreProtect can never
 * break FlowerWatch's own compile or classloading. Every failure mode
 * here degrades to "no CoreProtect cross-reference for this line", never
 * an exception that takes the rest of the plugin down with it.
 *
 * <p><b>Not yet verified against a live CoreProtect-CE 24.0 jar</b> —
 * matches this repo's own convention (see FoliaNexa's CLAUDE.md) of
 * being explicit about what's actually been run versus written against
 * a documented contract. If you're the one running this on the real
 * cluster: the first thing to check is whether {@link #initialize()}
 * reports success at all (it logs its reason either way), and if it
 * does, whether the formatted CoreProtect lines in the log actually
 * look sane rather than just non-empty.
 */
public final class CoreProtectBridge {

    private final Logger log;
    private volatile Object api; // net.coreprotect.CoreProtectAPI instance, or null if unavailable
    private Method blockLookupMethod;
    private Method parseResultMethod;

    public CoreProtectBridge(Logger log) {
        this.log = log;
    }

    /** @return a human-readable reason if unavailable, empty if ready. */
    public Optional<String> initialize() {
        api = null;
        Plugin coreProtectPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (coreProtectPlugin == null) {
            return Optional.of("CoreProtect is not installed on this server");
        }
        if (!coreProtectPlugin.isEnabled()) {
            return Optional.of("CoreProtect is installed but not enabled");
        }
        try {
            Method getApi = coreProtectPlugin.getClass().getMethod("getAPI");
            Object candidateApi = getApi.invoke(coreProtectPlugin);
            if (candidateApi == null) {
                return Optional.of("CoreProtect.getAPI() returned null");
            }
            Method isEnabled = candidateApi.getClass().getMethod("isEnabled");
            boolean enabled = (boolean) isEnabled.invoke(candidateApi);
            if (!enabled) {
                return Optional.of("CoreProtectAPI reports isEnabled() == false (CoreProtect may still be starting up)");
            }
            Method lookup = candidateApi.getClass().getMethod("blockLookup", Block.class, int.class);
            Method parse = candidateApi.getClass().getMethod("parseResult", String[].class);

            this.blockLookupMethod = lookup;
            this.parseResultMethod = parse;
            this.api = candidateApi;
            return Optional.empty();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return Optional.of("CoreProtect API shape didn't match what FlowerWatch expects ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + ") — this CoreProtect build's API may have changed");
        }
    }

    public boolean isAvailable() {
        return api != null;
    }

    /**
     * Looks up CoreProtect's own record(s) for {@code block} within the
     * last {@code lookbackSeconds} seconds. Must be called from the
     * thread that owns {@code block}'s region — this makes the same
     * synchronous call CoreProtect's own API expects a main/region-thread
     * caller to make (this is why FlowerWatch's listener calls it
     * directly from its (already region-owning) event handlers, rather
     * than hopping to another thread first).
     */
    @SuppressWarnings("unchecked")
    public List<String> lookup(Block block, int lookbackSeconds) {
        Object apiRef = api;
        if (apiRef == null) {
            return List.of();
        }
        try {
            List<String[]> rows = (List<String[]>) blockLookupMethod.invoke(apiRef, block, lookbackSeconds);
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<String> formatted = new ArrayList<>(rows.size());
            for (String[] row : rows) {
                formatted.add(formatRow(apiRef, row));
            }
            return formatted;
        } catch (ReflectiveOperationException | ClassCastException e) {
            log.warning("[FlowerWatch] CoreProtect lookup failed, disabling further lookups this session: " + e);
            api = null; // stop retrying every event once it's proven broken
            return List.of();
        }
    }

    private String formatRow(Object apiRef, String[] row) {
        try {
            Object parsed = parseResultMethod.invoke(apiRef, (Object) row);
            String described = describeParsedResult(parsed);
            return described.isBlank() ? String.join(",", row) : described;
        } catch (ReflectiveOperationException e) {
            // Fall back to the raw row rather than losing the data entirely.
            return String.join(",", row);
        }
    }

    private String describeParsedResult(Object parsed) {
        StringBuilder sb = new StringBuilder();
        appendGetter(sb, parsed, "getPlayer", "player");
        appendGetter(sb, parsed, "getActionId", "action");
        appendGetter(sb, parsed, "getType", "type");
        appendGetter(sb, parsed, "getTime", "time");
        appendGetter(sb, parsed, "isRolledBack", "rolledBack");
        return sb.toString().trim();
    }

    private void appendGetter(StringBuilder sb, Object target, String methodName, String label) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object value = m.invoke(target);
            sb.append(label).append('=').append(value).append(' ');
        } catch (ReflectiveOperationException ignored) {
            // That getter doesn't exist on this CoreProtect version's
            // ParseResult — skip it, keep whatever others did resolve.
        }
    }
}
