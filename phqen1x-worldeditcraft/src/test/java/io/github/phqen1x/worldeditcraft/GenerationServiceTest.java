package io.github.phqen1x.worldeditcraft;

import com.sun.net.httpserver.HttpServer;
import io.github.phqen1x.worldeditcraft.library.SchematicLibrary;
import io.github.phqen1x.worldeditcraft.llm.LemonadeClient;
import io.github.phqen1x.worldeditcraft.llm.LemonadeSettings;
import io.github.phqen1x.worldeditcraft.llm.MiniJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repair loop, end to end, against a real {@link HttpServer} — a fake
 * Lemonade that returns garbage, then prose-wrapped JSON that's still
 * invalid (unknown palette key), then a valid script. Matches the design
 * doc's {@code RepairLoopTest}: exactly {@code max-attempts} requests,
 * and each repair round's prompt carries the prior round's specific
 * validation errors.
 */
class GenerationServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void convergesAfterTwoRepairRoundsAndSavesTheResult(@TempDir Path tempDir) throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        List<String> capturedRequestBodies = new java.util.ArrayList<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            capturedRequestBodies.add(requestBody);
            int n = requestCount.incrementAndGet();

            String assistantContent = switch (n) {
                case 1 -> "Sure! I'd be happy to help with that structure design."; // no JSON at all
                case 2 -> "{\"name\": \"hall\", \"size\": [3,3,3], \"seed\": 1, "
                        + "\"palette\": {\"wall\": \"minecraft:stone\"}, "
                        + "\"ops\": [{\"op\": \"box\", \"from\": [0,0,0], \"to\": [1,1,1], \"block\": \"modded:dwarven_forge_block\"}]}"; // not a vanilla block
                default -> "{\"name\": \"hall\", \"size\": [3,3,3], \"seed\": 1, "
                        + "\"palette\": {\"wall\": \"minecraft:stone\"}, "
                        + "\"ops\": [{\"op\": \"box\", \"from\": [0,0,0], \"to\": [1,1,1], \"block\": \"wall\"}]}"; // valid
            };

            Map<String, Object> response = Map.of("choices", List.of(Map.of("message", Map.of("content", assistantContent))));
            sendJson(exchange, MiniJson.write(response));
        });
        registerModelManagementHandlers(server);
        server.start();

        LemonadeSettings settings = new LemonadeSettings("http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1", "test-model", "", 5, 10, 20, 0.4, 0.9, 512, 3, 2, 32);
        LemonadeClient client = new LemonadeClient(settings, Logger.getLogger("test"));

        SchematicLibrary library = SchematicLibrary.open(tempDir.resolve("schematics"));
        WorldEditCraftConfig config = testConfig(settings);
        GenerationService service = new GenerationService(client, config, library, tempDir.resolve("failed"));

        GenerationService.GenerationResult result = service.generate("a small stone hall", null);

        assertTrue(result.success(), "expected the third attempt to succeed: " + result.errorMessage());
        assertEquals(3, requestCount.get());
        assertEquals("hall", result.slug());
        assertTrue(library.load("hall").isPresent());

        // Round 2's request must carry round 1's specific complaint (no JSON found).
        assertTrue(capturedRequestBodies.get(1).contains("JSON object"));
        // Round 3's request must carry round 2's specific complaint (the non-vanilla block).
        assertTrue(capturedRequestBodies.get(2).contains("dwarven_forge_block"));
    }

    @Test
    void exhaustingAllAttemptsReturnsFailureAndWritesTheFailedResponse(@TempDir Path tempDir) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            Map<String, Object> response = Map.of("choices", List.of(Map.of("message", Map.of("content", "not json, ever"))));
            sendJson(exchange, MiniJson.write(response));
        });
        registerModelManagementHandlers(server);
        server.start();

        LemonadeSettings settings = new LemonadeSettings("http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1", "test-model", "", 5, 10, 20, 0.4, 0.9, 512, 2, 2, 32);
        LemonadeClient client = new LemonadeClient(settings, Logger.getLogger("test"));
        SchematicLibrary library = SchematicLibrary.open(tempDir.resolve("schematics"));
        Path failedDir = tempDir.resolve("failed");
        WorldEditCraftConfig config = testConfig(settings);
        GenerationService service = new GenerationService(client, config, library, failedDir);

        GenerationService.GenerationResult result = service.generate("something", null);

        assertFalse(result.success());
        assertEquals(2, result.attempts());
        assertTrue(java.nio.file.Files.exists(failedDir), "keep-failed-responses is on by default in testConfig()");
    }

    @Test
    void generatePullsAndLoadsTheConfiguredModelBeforeTheFirstChatRequest(@TempDir Path tempDir) throws IOException {
        List<String> pathsHit = new java.util.ArrayList<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/pull", exchange -> {
            pathsHit.add("pull");
            sendJson(exchange, "{\"status\":\"success\",\"message\":\"Installed model: test-model\"}");
        });
        server.createContext("/api/v1/load", exchange -> {
            pathsHit.add("load");
            sendJson(exchange, "{\"status\":\"success\",\"message\":\"Loaded model: test-model\"}");
        });
        server.createContext("/api/v1/chat/completions", exchange -> {
            pathsHit.add("chat");
            String script = "{\"name\": \"hall\", \"size\": [2,2,2], \"seed\": 1, "
                    + "\"palette\": {\"wall\": \"minecraft:stone\"}, "
                    + "\"ops\": [{\"op\": \"box\", \"from\": [0,0,0], \"to\": [1,1,1], \"block\": \"wall\"}]}";
            Map<String, Object> response = Map.of("choices", List.of(Map.of("message", Map.of("content", script))));
            sendJson(exchange, MiniJson.write(response));
        });
        server.start();

        LemonadeSettings settings = new LemonadeSettings("http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1", "test-model", "", 5, 10, 20, 0.4, 0.9, 512, 3, 2, 32);
        LemonadeClient client = new LemonadeClient(settings, Logger.getLogger("test"));
        SchematicLibrary library = SchematicLibrary.open(tempDir.resolve("schematics"));
        GenerationService service = new GenerationService(client, testConfig(settings), library, tempDir.resolve("failed"));

        GenerationService.GenerationResult result = service.generate("a small hall", null);

        assertTrue(result.success());
        assertEquals(List.of("pull", "load", "chat"), pathsHit, "pull and load must both happen, in order, before the first chat request");
    }

    @Test
    void generateFailsWithoutCallingChatCompletionsWhenThePullFails(@TempDir Path tempDir) throws IOException {
        List<String> pathsHit = new java.util.ArrayList<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/pull", exchange -> {
            pathsHit.add("pull");
            sendJsonWithStatus(exchange, 404, "{\"detail\":\"no such model\"}");
        });
        server.createContext("/api/v1/chat/completions", exchange -> {
            pathsHit.add("chat");
            sendJson(exchange, "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");
        });
        server.start();

        LemonadeSettings settings = new LemonadeSettings("http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1", "no-such-model", "", 5, 10, 20, 0.4, 0.9, 512, 3, 2, 32);
        LemonadeClient client = new LemonadeClient(settings, Logger.getLogger("test"));
        SchematicLibrary library = SchematicLibrary.open(tempDir.resolve("schematics"));
        GenerationService service = new GenerationService(client, testConfig(settings), library, tempDir.resolve("failed"));

        GenerationService.GenerationResult result = service.generate("a small hall", null);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("no-such-model"));
        assertEquals(List.of("pull"), pathsHit, "chat/completions must never be called once the pull fails");
    }

    private static WorldEditCraftConfig testConfig(LemonadeSettings settings) {
        return new WorldEditCraftConfig(
                settings,
                new WorldEditCraftConfig.Generation(128, 400_000, 400, new int[]{32, 24, 32}, "vanilla", List.of(), true),
                new WorldEditCraftConfig.Paste(2048, false, "air", 10, 2, 60),
                new WorldEditCraftConfig.Library("schematics", 500),
                new WorldEditCraftConfig.WorldEdit(false)
        );
    }

    /** Stubs /api/v1/pull and /api/v1/load with a bare "success" — for tests whose focus is the repair loop, not model management. */
    private static void registerModelManagementHandlers(HttpServer server) {
        server.createContext("/api/v1/pull", exchange -> sendJson(exchange, "{\"status\":\"success\",\"message\":\"Installed model: test-model\"}"));
        server.createContext("/api/v1/load", exchange -> sendJson(exchange, "{\"status\":\"success\",\"message\":\"Loaded model: test-model\"}"));
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        sendJsonWithStatus(exchange, 200, body);
    }

    private static void sendJsonWithStatus(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
