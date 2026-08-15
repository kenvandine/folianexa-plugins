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
import java.util.Optional;
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

    private String startServerReturning(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void fetchPlayerParsesRealResponseShape() throws IOException {
        String body = """
                {"uuid":"abc","username":"Steve","first_seen":"2026-08-15T00:00:00","last_seen":"2026-08-15T00:00:00",
                 "stats":{"kills":5,"deaths":1,"blocks_mined":200,"playtime_seconds_total":3600},
                 "playtime_daily":[{"date":"2026-08-15","seconds":600}]}
                """;
        String baseUrl = startServerReturning(200, body);
        HttpMgmtClient client = new HttpMgmtClient(baseUrl, "token", Logger.getLogger("test"));

        Optional<HttpMgmtClient.PlayerBaseline> baseline = client.fetchPlayer("abc");

        assertTrue(baseline.isPresent());
        assertEquals(5.0, baseline.get().kills());
        assertEquals(1.0, baseline.get().deaths());
        assertEquals(200.0, baseline.get().blocksMined());
        assertEquals(3600.0, baseline.get().playtimeSecondsTotal());
    }

    @Test
    void fetchPlayer404ReturnsZeroBaselineNotEmpty() throws IOException {
        String baseUrl = startServerReturning(404, "{\"detail\":\"no such player\"}");
        HttpMgmtClient client = new HttpMgmtClient(baseUrl, "token", Logger.getLogger("test"));

        Optional<HttpMgmtClient.PlayerBaseline> baseline = client.fetchPlayer("unknown-uuid");

        assertTrue(baseline.isPresent(), "a genuine 404 (unknown player) must resolve to a real zero baseline, not empty");
        assertEquals(HttpMgmtClient.PlayerBaseline.ZERO, baseline.get());
    }

    @Test
    void fetchPlayerServerErrorReturnsEmptyNotZero() throws IOException {
        String baseUrl = startServerReturning(500, "internal error");
        HttpMgmtClient client = new HttpMgmtClient(baseUrl, "token", Logger.getLogger("test"));

        Optional<HttpMgmtClient.PlayerBaseline> baseline = client.fetchPlayer("abc");

        assertFalse(baseline.isPresent(), "a transient mgmt failure must not be treated as \"no existing data\"");
    }

    @Test
    void reportStatsSendsExpectedJsonAndAuthHeader() throws IOException, InterruptedException {
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

        HttpMgmtClient client = new HttpMgmtClient(baseUrl, "secret-token", Logger.getLogger("test"));
        StatsTracker.PlayerReport report = new StatsTracker.PlayerReport(
                "abc", "Steve", Map.of("kills", 5.0), Map.of("2026-08-15", 600L));
        client.reportStats(List.of(report));

        // The HTTP call happens synchronously inside reportStats (this
        // test calls it directly off any scheduler, unlike the real
        // plugin which always does so via AsyncScheduler), so the
        // request has already landed by the time send() returns.
        assertEquals("/api/v1/stats/report", receivedPath.get());
        assertEquals("Bearer secret-token", receivedAuth.get());
        assertTrue(receivedBody.get().contains("\"uuid\":\"abc\""), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"kills\":5"), receivedBody.get());
        assertTrue(receivedBody.get().contains("\"2026-08-15\":600"), receivedBody.get());
    }

    @Test
    void reportStatsWithEmptyListSendsNoRequest() throws IOException {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            called.set(true);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        HttpMgmtClient client = new HttpMgmtClient(baseUrl, "token", Logger.getLogger("test"));
        client.reportStats(List.of());

        assertFalse(called.get());
    }
}
