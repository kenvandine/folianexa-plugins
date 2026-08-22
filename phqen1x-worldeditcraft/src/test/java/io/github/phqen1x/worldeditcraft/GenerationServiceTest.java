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
        server.start();

        LemonadeSettings settings = new LemonadeSettings("http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1", "test-model", "", 5, 10, 0.4, 0.9, 512, 3, 2, 32);
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
        server.start();

        LemonadeSettings settings = new LemonadeSettings("http://127.0.0.1:" + server.getAddress().getPort(),
                "/api/v1", "test-model", "", 5, 10, 0.4, 0.9, 512, 2, 2, 32);
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

    private static WorldEditCraftConfig testConfig(LemonadeSettings settings) {
        return new WorldEditCraftConfig(
                settings,
                new WorldEditCraftConfig.Generation(128, 400_000, 400, new int[]{32, 24, 32}, "vanilla", List.of(), true),
                new WorldEditCraftConfig.Paste(2048, false, "air", 10, 2, 60),
                new WorldEditCraftConfig.Library("schematics", 500),
                new WorldEditCraftConfig.WorldEdit(false)
        );
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
