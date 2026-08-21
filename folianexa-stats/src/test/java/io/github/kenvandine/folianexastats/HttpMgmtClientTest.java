package io.github.kenvandine.folianexastats;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises HttpMgmtClient against a real (JDK built-in) local HTTP
 * server rather than mocking java.net.http.HttpClient — closer to how
 * this actually behaves against real folia-nexa-mgmt.
 */
class HttpMgmtClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private StatsTracker.PlayerReport sampleReport() {
        return new StatsTracker.PlayerReport(
                "abc", "Steve", Map.of("kills", 5.0), Map.of("auraskills_power_level", 3.0), Map.of("2026-08-15", 600L));
    }

    private HttpMgmtClient client(String baseUrl, String apiToken) {
        return new HttpMgmtClient(baseUrl, apiToken, "overworld", Logger.getLogger("test"));
    }

    @Test
    void reportStatsSendsExpectedJsonAndAuthHeaderAndReturnsTrueOn200() throws IOException, InterruptedException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        AtomicReference<String> receivedPath = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        HttpMgmtClient client = client(baseUrl, "secret-token");
        boolean ok = client.reportStats(List.of(sampleReport()));

        // The HTTP call happens synchronously inside reportStats (this
        // test calls it directly off any scheduler, unlike the real
        // plugin which always does so via AsyncScheduler), so the
        // request has already landed by the time send() returns.
        assertTrue(ok);
        assertEquals("/api/v1/stats/report", receivedPath.get());
        assertEquals("Bearer secret-token", receivedAuth.get());
        assertTrue(receivedBody.get().contains("\"world\":\"overworld\""), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"uuid\":\"abc\""), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"stat_deltas\""), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"kills\":5"), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"gauges\""), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"auraskills_power_level\":3"), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"2026-08-15\":600"), receivedBody.get());
    }

    @Test
    void reportStatsReturnsFalseOnNon200() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] resp = "{\"detail\":\"missing bearer token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        HttpMgmtClient client = client(baseUrl, "wrong-token");
        boolean ok = client.reportStats(List.of(sampleReport()));

        assertFalse(ok, "caller must not confirm/clear deltas that mgmt actually rejected");
    }

    @Test
    void reportStatsWithEmptyListSendsNoRequestAndReturnsTrue() throws IOException {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            called.set(true);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        HttpMgmtClient client = client(baseUrl, "token");
        boolean ok = client.reportStats(List.of());

        assertFalse(called.get());
        assertTrue(ok, "nothing to send is trivially a success, not a failure to retry");
    }

    @Test
    void blankBaseUrlDoesNotThrowAndReportStatsReturnsTrue() {
        // A blank mgmt-base-url (unset config) must not attempt a
        // request at all — URI.create("" + path) is a relative URI, and
        // HttpRequest.Builder#uri() throws IllegalArgumentException for
        // that synchronously, before this class's try/catch even starts.
        // Without the enabled-guard, this would escape as an uncaught
        // exception on every report cycle instead of the "disabled until
        // configured" behavior the plugin warns about at startup.
        HttpMgmtClient client = client("", "token");

        boolean ok = client.reportStats(List.of(sampleReport()));

        assertTrue(ok, "disabled (no baseUrl) must not be reported as a failure the caller retries forever");
    }

    @Test
    void malformedBaseUrlDoesNotThrowAndReturnsFalse() {
        // Any other syntactically-broken value (missing scheme, stray
        // whitespace, ...) must be swallowed the same way a network
        // failure is, not escape as an uncaught IllegalArgumentException —
        // and unlike the blank case above, this one IS enabled and really
        // did fail to send, so the caller must not confirm the deltas.
        HttpMgmtClient client = client("mgmt.internal:8443", "token");

        boolean ok = client.reportStats(List.of(sampleReport()));

        assertFalse(ok);
    }
}
