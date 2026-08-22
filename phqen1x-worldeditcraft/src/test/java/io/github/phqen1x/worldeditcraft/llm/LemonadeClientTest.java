package io.github.phqen1x.worldeditcraft.llm;

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
 * Against a real {@link HttpServer} on {@code 127.0.0.1:0}, the pattern
 * from {@code folianexa-stats/src/test/java/.../HttpMgmtClientTest.java} —
 * no mocking library, a genuine socket and a genuine HTTP exchange.
 */
class LemonadeClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsExpectedRequestBodyAndParsesASuccessfulResponse() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server = startServer("/api/v1/chat/completions", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"name\\\":\\\"hall\\\"}\"}}]}";
            sendJson(exchange, 200, response);
        });

        LemonadeClient client = clientFor(server);
        LemonadeClient.ChatResult result = client.chatCompletion(List.of(Map.of("role", "user", "content", "a small hall")));

        assertTrue(result.success());
        assertEquals("{\"name\":\"hall\"}", result.content());
        assertTrue(capturedBody.get().contains("\"messages\""));
        assertTrue(capturedBody.get().contains("a small hall"));
    }

    @Test
    void handles404() throws IOException {
        server = startServer("/api/v1/chat/completions", exchange -> sendJson(exchange, 404, "{\"error\":\"not found\"}"));
        LemonadeClient.ChatResult result = clientFor(server).chatCompletion(List.of(Map.of("role", "user", "content", "x")));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("404"));
    }

    @Test
    void handles500() throws IOException {
        server = startServer("/api/v1/chat/completions", exchange -> sendJson(exchange, 500, "internal error"));
        LemonadeClient.ChatResult result = clientFor(server).chatCompletion(List.of(Map.of("role", "user", "content", "x")));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("500"));
    }

    @Test
    void handlesMalformedJsonResponse() throws IOException {
        server = startServer("/api/v1/chat/completions", exchange -> sendJson(exchange, 200, "not json at all {{{"));
        LemonadeClient.ChatResult result = clientFor(server).chatCompletion(List.of(Map.of("role", "user", "content", "x")));
        assertFalse(result.success());
    }

    @Test
    void handlesConnectionRefusedWithoutThrowing() {
        // Nothing is listening on this port.
        LemonadeSettings settings = new LemonadeSettings("http://127.0.0.1:1", "/api/v1", "test-model", "",
                1, 2, 0.4, 0.9, 512, 3, 2, 32);
        LemonadeClient client = new LemonadeClient(settings, Logger.getLogger("test"));
        LemonadeClient.ChatResult result = client.chatCompletion(List.of(Map.of("role", "user", "content", "x")));
        assertFalse(result.success());
    }

    @Test
    void listModelsParsesModelIds() throws IOException {
        server = startServer("/api/v1/models", exchange ->
                sendJson(exchange, 200, "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}"));
        LemonadeClient.ModelsResult result = clientFor(server).listModels();
        assertTrue(result.success());
        assertEquals(List.of("model-a", "model-b"), result.models());
    }

    @Test
    void sendsAuthorizationHeaderOnlyWhenApiKeyIsNonBlank() throws IOException {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server = startServer("/api/v1/chat/completions", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");
        });

        LemonadeSettings withKey = new LemonadeSettings(baseUrlOf(server), "/api/v1", "m", "secret-token",
                5, 10, 0.4, 0.9, 512, 3, 2, 32);
        new LemonadeClient(withKey, Logger.getLogger("test")).chatCompletion(List.of(Map.of("role", "user", "content", "x")));
        assertEquals("Bearer secret-token", authHeader.get());
    }

    private static LemonadeClient clientFor(HttpServer server) {
        LemonadeSettings settings = new LemonadeSettings(baseUrlOf(server), "/api/v1", "test-model", "",
                5, 10, 0.4, 0.9, 512, 3, 2, 32);
        return new LemonadeClient(settings, Logger.getLogger("test"));
    }

    private static String baseUrlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    private static HttpServer startServer(String path, Handler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler::handle);
        server.start();
        return server;
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
