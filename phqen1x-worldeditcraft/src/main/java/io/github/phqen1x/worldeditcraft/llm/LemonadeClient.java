package io.github.phqen1x.worldeditcraft.llm;

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
 * Talks to a Lemonade Server's OpenAI-compatible API — {@code POST
 * {api-path}/chat/completions} and {@code GET {api-path}/models}. No
 * {@code org.bukkit} imports, but this does real I/O — callers must only
 * ever invoke it from {@code Bukkit.getAsyncScheduler()}, never a
 * game-tick thread (see docs/phqen1x-rpg-suite/07-folia-safety.md).
 *
 * <p>Failures are returned, not thrown — a generation that fails should
 * tell the operator why, not propagate an exception into a scheduler
 * thread. {@link InterruptedException} re-sets the interrupt flag.
 *
 * <p>Pins {@code HTTP/1.1} explicitly. {@code folianexa-stats}'s {@code
 * HttpMgmtClient} documents a live-confirmed bug against this exact
 * server stack (Lemonade is also a uvicorn application): the JDK {@link
 * HttpClient} defaults to preferring HTTP/2, which over plaintext means
 * attempting an {@code Upgrade: h2c} handshake on every request, and a
 * uvicorn/h11 server that doesn't support it can intermittently corrupt
 * request framing on a reused connection rather than failing cleanly —
 * alternating bogus 422/400 responses. Pinning the version here skips
 * the upgrade attempt entirely (see docs/phqen1x-rpg-suite/04-lemonade-integration.md
 * for the full account).
 */
public final class LemonadeClient {

    private final LemonadeSettings settings;
    private final HttpClient httpClient;
    private final Logger logger;

    public LemonadeClient(LemonadeSettings settings, Logger logger) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .build();
        this.logger = logger;
    }

    public record ChatResult(boolean success, String content, String errorMessage, long latencyMillis) {
        public static ChatResult ok(String content, long latencyMillis) {
            return new ChatResult(true, content, null, latencyMillis);
        }

        public static ChatResult failure(String errorMessage, long latencyMillis) {
            return new ChatResult(false, null, errorMessage, latencyMillis);
        }
    }

    public record ModelsResult(boolean success, List<String> models, String errorMessage) {
        public static ModelsResult ok(List<String> models) {
            return new ModelsResult(true, models, null);
        }

        public static ModelsResult failure(String errorMessage) {
            return new ModelsResult(false, List.of(), errorMessage);
        }
    }

    /**
     * One chat-completion call. {@code messages} is an ordered list of
     * {@code {"role": ..., "content": ...}} maps — the caller ({@code
     * PromptBuilder} plus the repair loop) owns conversation shape; this
     * class only sends it.
     */
    public ChatResult chatCompletion(List<Map<String, String>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", settings.temperature());
        body.put("top_p", settings.topP());
        body.put("max_tokens", settings.maxTokens());
        String json = MiniJson.write(body);

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(settings.chatCompletionsUrl()))
                    .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (!settings.apiKey().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + settings.apiKey());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            logger.fine(() -> "Lemonade request: " + json);
            logger.fine(() -> "Lemonade response (" + response.statusCode() + "): " + response.body());

            if (response.statusCode() != 200) {
                return ChatResult.failure("HTTP " + response.statusCode() + ": " + response.body(), latency);
            }
            String content = extractMessageContent(response.body());
            if (content == null) {
                return ChatResult.failure("Response did not contain choices[0].message.content: " + response.body(), latency);
            }
            return ChatResult.ok(content, latency);
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            long latency = System.currentTimeMillis() - start;
            logger.log(Level.WARNING, "Lemonade chat completion failed", e);
            return ChatResult.failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), latency);
        }
    }

    public ModelsResult listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.modelsUrl()))
                    .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ModelsResult.failure("HTTP " + response.statusCode() + ": " + response.body());
            }
            return ModelsResult.ok(extractModelIds(response.body()));
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.log(Level.WARNING, "Lemonade model listing failed", e);
            return ModelsResult.failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractMessageContent(String responseBody) {
        Object parsed;
        try {
            parsed = MiniJson.parse(responseBody);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        if (!(choices.get(0) instanceof Map<?, ?> first) || !(first.get("message") instanceof Map<?, ?> message)) {
            return null;
        }
        Object content = message.get("content");
        return content instanceof String s ? s : null;
    }

    private static List<String> extractModelIds(String responseBody) {
        Object parsed;
        try {
            parsed = MiniJson.parse(responseBody);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("data") instanceof List<?> data)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object entry : data) {
            if (entry instanceof Map<?, ?> model && model.get("id") instanceof String id) {
                ids.add(id);
            }
        }
        return ids;
    }
}
