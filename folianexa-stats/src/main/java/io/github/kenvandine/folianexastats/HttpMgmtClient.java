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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Talks to folia-nexa-mgmt's stats API — {@code POST
 * /api/v1/stats/report}. No {@code org.bukkit.*} imports, but this does
 * real I/O — callers (see {@link FoliaNexaStatsPlugin}) must only ever
 * invoke it from {@code Bukkit.getAsyncScheduler()}, never a game-tick
 * thread (see docs/plugin-dev/02-plugin-architecture.md in the FoliaNexa
 * repo).
 *
 * <p>Failures are logged and swallowed rather than thrown — a plugin
 * whose stats reporting can crash a world's JVM would be a much worse
 * failure mode than a gap in the leaderboard — but unlike the earlier
 * "absolute total" design this replaced, a failure here is no longer
 * harmless to just ignore: {@link #reportStats} now sends deltas (see
 * {@link StatsTracker}'s class docs), and returns whether the send
 * actually succeeded so the caller only clears those deltas
 * ({@link StatsTracker#confirmReported}) once mgmt has really received
 * them, rather than losing that cycle's progress on a network blip.
 */
final class HttpMgmtClient {

    private final String baseUrl;
    // A blank or malformed baseUrl (unset config, typo missing "http://",
    // stray whitespace, ...) makes URI.create()/HttpRequest.Builder#uri()
    // throw IllegalArgumentException — synchronously, before the method
    // below ever reaches its try block. Short-circuiting on this instead of
    // attempting the request keeps that class of misconfiguration inside
    // this class's "log and swallow, never throw" contract (see class docs)
    // rather than letting it escape as an uncaught exception on every
    // report cycle.
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
     * Returns whether every player in {@code reports} was actually
     * delivered to mgmt — the caller must only call
     * {@link StatsTracker#confirmReported} when this is {@code true}.
     * {@code true} on an empty list or while disabled (no baseUrl
     * configured) too — there was nothing to fail to send.
     */
    boolean reportStats(List<StatsTracker.PlayerReport> reports) {
        if (reports.isEmpty() || !enabled) return true;

        List<Object> playersJson = new ArrayList<>();
        for (StatsTracker.PlayerReport report : reports) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", report.uuid());
            entry.put("username", report.username());
            entry.put("stat_deltas", report.statDeltas());
            entry.put("gauges", report.gauges());
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
                return false;
            }
            return true;
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "stats report failed", e);
            return false;
        }
    }
}
