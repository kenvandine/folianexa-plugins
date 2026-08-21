package io.github.kenvandine.folianexastats;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Talks to folia-nexa-mgmt's stats API — {@code GET
 * /api/v1/public/players/{uuid}} to seed a player's baseline (see
 * {@link StatsTracker}'s class docs for why that's needed), and {@code
 * POST /api/v1/stats/report} to actually report. No {@code org.bukkit.*}
 * imports, but this does real I/O — callers (see
 * {@link FoliaNexaStatsPlugin}) must only ever invoke it from
 * {@code Bukkit.getAsyncScheduler()}, never a game-tick thread (see
 * docs/plugin-dev/02-plugin-architecture.md in the FoliaNexa repo).
 *
 * <p>Failures are logged and swallowed rather than thrown — a report
 * that fails this cycle is superseded by the next one (kill/death/block
 * totals are cumulative, not deltas, so nothing is lost by skipping a
 * cycle), and a plugin whose stats reporting can crash a world's JVM
 * would be a much worse failure mode than a gap in the leaderboard.
 */
final class HttpMgmtClient {

    record PlayerBaseline(double kills, double deaths, double blocksMined, double playtimeSecondsTotal) {
        static final PlayerBaseline ZERO = new PlayerBaseline(0, 0, 0, 0);
    }

    private final String baseUrl;
    // A blank or malformed baseUrl (unset config, typo missing "http://",
    // stray whitespace, ...) makes URI.create()/HttpRequest.Builder#uri()
    // throw IllegalArgumentException — synchronously, before either method
    // below ever reaches its try block. Short-circuiting on this instead of
    // attempting the request keeps that class of misconfiguration inside
    // this class's "log and swallow, never throw" contract (see class docs)
    // rather than letting it escape as an uncaught exception on every fetch
    // and every report cycle.
    private final boolean enabled;
    private final String apiToken;
    private final HttpClient httpClient;
    private final Logger logger;

    HttpMgmtClient(String baseUrl, String apiToken, Logger logger) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.enabled = !baseUrl.isBlank();
        this.apiToken = apiToken;
        // HTTP_1_1 explicitly: java.net.http.HttpClient defaults to
        // preferring HTTP/2, which over plaintext means attempting an
        // "Upgrade: h2c" handshake (RFC 7540 §3.2) on every request.
        // folia-nexa-mgmt is a plain uvicorn/h11 server — HTTP/1.1 only,
        // no h2c support — and the mismatch didn't just fail cleanly; it
        // intermittently corrupted request framing on this client's
        // reused connection, confirmed live: alternating "422 Field
        // required, input: null" (mgmt received an empty body) and a
        // bare "400 Invalid HTTP request received" from uvicorn itself,
        // never a clean, consistent failure. Pinning HTTP/1.1 here skips
        // the upgrade attempt entirely.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.logger = logger;
    }

    /**
     * Empty means the fetch itself failed (network error, unexpected
     * status) — callers should NOT treat that as "no existing data" and
     * must retry later rather than seeding a zero baseline, or a
     * transient mgmt outage would silently regress every player's
     * reported total. {@link PlayerBaseline#ZERO} (a real present value,
     * not empty) is what a genuine 404 — a player mgmt has never heard of
     * — returns.
     */
    Optional<PlayerBaseline> fetchPlayer(String uuid) {
        if (!enabled) return Optional.empty();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/public/players/" + uuid))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.of(PlayerBaseline.ZERO);
            }
            if (response.statusCode() != 200) {
                logger.warning("fetching baseline for " + uuid + " failed: HTTP " + response.statusCode());
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) MiniJson.parse(response.body());
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) body.getOrDefault("stats", Map.of());
            return Optional.of(new PlayerBaseline(
                    numberOrZero(stats.get("kills")),
                    numberOrZero(stats.get("deaths")),
                    numberOrZero(stats.get("blocks_mined")),
                    numberOrZero(stats.get("playtime_seconds_total"))
            ));
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "fetching baseline for " + uuid + " failed", e);
            return Optional.empty();
        }
    }

    private static double numberOrZero(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    void reportStats(List<StatsTracker.PlayerReport> reports) {
        if (reports.isEmpty() || !enabled) return;

        List<Object> playersJson = new ArrayList<>();
        for (StatsTracker.PlayerReport report : reports) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", report.uuid());
            entry.put("username", report.username());
            entry.put("stats", report.stats());
            entry.put("playtime_daily", report.playtimeDaily());
            playersJson.add(entry);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("players", playersJson);
        String json = MiniJson.write(body);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/stats/report"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warning("stats report rejected: HTTP " + response.statusCode() + " " + response.body());
            }
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "stats report failed", e);
        }
    }
}
