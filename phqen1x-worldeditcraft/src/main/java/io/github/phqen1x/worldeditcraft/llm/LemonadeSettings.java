package io.github.phqen1x.worldeditcraft.llm;

/**
 * Everything {@code config.yml}'s {@code lemonade:} section carries —
 * base URL, model, sampling, timeouts, and the repair/concurrency knobs.
 * Loaded by {@code ConfigLoader}; nothing here reads {@code config.yml}
 * directly, keeping this class free of any {@code org.bukkit} import.
 */
public record LemonadeSettings(
        String baseUrl,
        String apiPath,
        String model,
        String apiKey,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds,
        int pullTimeoutSeconds,
        double temperature,
        double topP,
        int maxTokens,
        int maxAttempts,
        int maxConcurrentRequests,
        int queueCapacity
) {
    public String chatCompletionsUrl() {
        return normalizedBaseUrl() + apiPath + "/chat/completions";
    }

    public String modelsUrl() {
        return normalizedBaseUrl() + apiPath + "/models";
    }

    /** Lemonade's own management endpoint — installs a model (downloads it) if it isn't already on disk. */
    public String pullUrl() {
        return normalizedBaseUrl() + apiPath + "/pull";
    }

    /** Lemonade's own management endpoint — loads a model into memory, downloading it first if necessary. */
    public String loadUrl() {
        return normalizedBaseUrl() + apiPath + "/load";
    }

    private String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
