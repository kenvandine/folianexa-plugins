package io.github.phqen1x.worldeditcraft.llm;

import java.util.List;

/**
 * Backs {@code /wec status} — reachability, the model list, the last
 * call's outcome. {@code structuredOutputSupported} is {@code null}
 * until the client has actually probed for it (see the design doc's
 * "The client probes for response_format support once at startup").
 */
public record LemonadeStatus(
        boolean reachable,
        List<String> availableModels,
        String loadedModel,
        String lastError,
        long lastLatencyMillis,
        Boolean structuredOutputSupported
) {
    public LemonadeStatus {
        availableModels = List.copyOf(availableModels);
    }
}
