package io.github.phqen1x.worldeditcraft;

import io.github.phqen1x.worldeditcraft.dsl.BuildScriptInterpreter;
import io.github.phqen1x.worldeditcraft.dsl.BuildScriptParser;
import io.github.phqen1x.worldeditcraft.dsl.BuildScriptValidator;
import io.github.phqen1x.worldeditcraft.dsl.ValidationIssue;
import io.github.phqen1x.worldeditcraft.library.SchematicLibrary;
import io.github.phqen1x.worldeditcraft.library.SchematicRecord;
import io.github.phqen1x.worldeditcraft.llm.JsonCoercion;
import io.github.phqen1x.worldeditcraft.llm.LemonadeClient;
import io.github.phqen1x.worldeditcraft.llm.PromptBuilder;
import io.github.phqen1x.worldeditcraft.schem.SchematicMeta;
import io.github.phqen1x.worldeditcraft.schem.SchematicWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The generate pipeline end to end: prompt -> Lemonade -> extract -> parse
 * -> validate -> repair-or-continue -> rasterize -> save. No {@code
 * org.bukkit} import, but this drives {@link LemonadeClient} and {@link
 * SchematicLibrary}, both real I/O — callers must only invoke {@link
 * #generate} from {@code Bukkit.getAsyncScheduler()} (see
 * docs/phqen1x-rpg-suite/07-folia-safety.md: "Steps 1-8 never touch a
 * game thread"). This class only ever reaches step 8 (the library
 * write); pasting (step 9) is a separate, not-yet-implemented concern —
 * see the plugin README's milestone status.
 */
public final class GenerationService {

    public record GenerationResult(boolean success, String slug, List<ValidationIssue> issues, String errorMessage, int attempts) {
        public GenerationResult {
            issues = List.copyOf(issues);
        }
    }

    private final LemonadeClient client;
    private final WorldEditCraftConfig config;
    private final SchematicLibrary library;
    private final Path failedResponsesDirectory;

    public GenerationService(LemonadeClient client, WorldEditCraftConfig config, SchematicLibrary library, Path failedResponsesDirectory) {
        this.client = client;
        this.config = config;
        this.library = library;
        this.failedResponsesDirectory = failedResponsesDirectory;
    }

    public GenerationResult generate(String brief, String requestedName) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", PromptBuilder.buildSystemPrompt(config.generation().toLimits())));
        messages.add(Map.of("role", "user", "content", PromptBuilder.buildUserMessage(brief, config.generation().defaultSize())));

        List<ValidationIssue> lastIssues = List.of();
        String lastRawContent = null;
        int maxAttempts = Math.max(1, config.lemonade().maxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            LemonadeClient.ChatResult chat = client.chatCompletion(messages);
            if (!chat.success()) {
                return new GenerationResult(false, null, lastIssues, "Lemonade request failed: " + chat.errorMessage(), attempt);
            }
            lastRawContent = chat.content();
            messages.add(Map.of("role", "assistant", "content", chat.content()));

            RoundOutcome outcome = tryOneRound(chat.content(), brief, requestedName, attempt);
            if (outcome.result() != null) {
                return outcome.result();
            }
            lastIssues = outcome.issues();
            messages.add(repairMessage(lastIssues));
        }

        if (config.generation().keepFailedResponses() && lastRawContent != null) {
            writeFailedResponse(lastRawContent);
        }
        return new GenerationResult(false, null, lastIssues,
                "Generation failed after " + maxAttempts + " attempt(s). See the accumulated issues.", maxAttempts);
    }

    private record RoundOutcome(GenerationResult result, List<ValidationIssue> issues) {
        static RoundOutcome success(GenerationResult result) {
            return new RoundOutcome(result, List.of());
        }

        static RoundOutcome repair(List<ValidationIssue> issues) {
            return new RoundOutcome(null, issues);
        }
    }

    /** A non-null {@link RoundOutcome#result} means the round succeeded (or failed with no more attempts left to try); otherwise {@link RoundOutcome#issues} feeds the next repair round. */
    private RoundOutcome tryOneRound(String rawContent, String brief, String requestedName, int attempt) {
        Object rawJson;
        try {
            rawJson = JsonCoercion.extractAndParse(rawContent);
        } catch (IllegalArgumentException e) {
            return RoundOutcome.repair(List.of(ValidationIssue.fatal(-1, "No JSON object found in the model's response.",
                    "Your response did not contain a JSON object. Return a single JSON object and nothing else.")));
        }

        BuildScriptParser.ParseResult parsed = BuildScriptParser.parse(rawJson);
        if (parsed.issues().stream().anyMatch(ValidationIssue::triggersRepair)) {
            return RoundOutcome.repair(parsed.issues());
        }

        BuildScriptValidator.ValidationResult validated = BuildScriptValidator.validate(parsed.script(), config.generation().toLimits());
        if (validated.shouldRepair()) {
            return RoundOutcome.repair(validated.issues());
        }

        BuildScriptInterpreter.Result interpreted = BuildScriptInterpreter.interpret(validated, parsed.script().palette(), parsed.script().seed());
        Optional<ValidationIssue> emptyIssue = BuildScriptValidator.checkNotEmpty(interpreted.grid());
        if (emptyIssue.isPresent()) {
            return RoundOutcome.repair(List.of(emptyIssue.get()));
        }

        String name = requestedName != null && !requestedName.isBlank() ? requestedName : parsed.script().name();
        String slug = saveGeneratedSchematic(interpreted, name, brief);
        return RoundOutcome.success(new GenerationResult(true, slug, validated.issues(), null, attempt));
    }

    private String saveGeneratedSchematic(BuildScriptInterpreter.Result interpreted, String name, String brief) {
        SchematicMeta preliminaryMeta = new SchematicMeta(name, "Phqen1xWorldEditCraft", Instant.now().toEpochMilli(),
                brief, config.lemonade().model(), List.of(), "");
        byte[] schemBytes = SchematicWriter.write(interpreted.grid(), preliminaryMeta, new int[]{0, 0, 0});
        String checksum = sha256Hex(schemBytes);

        SchematicRecord record = new SchematicRecord(name, preliminaryMeta.author(), preliminaryMeta.dateEpochMillis(),
                brief, preliminaryMeta.model(), List.of(), checksum,
                interpreted.grid().width(), interpreted.grid().height(), interpreted.grid().length());
        return library.save(name, schemBytes, record);
    }

    private static Map<String, String> repairMessage(List<ValidationIssue> issues) {
        return Map.of("role", "user", "content", PromptBuilder.buildRepairMessage(issues));
    }

    private void writeFailedResponse(String rawContent) {
        try {
            Files.createDirectories(failedResponsesDirectory);
            String filename = Instant.now().toEpochMilli() + "-failed.txt";
            Files.writeString(failedResponsesDirectory.resolve(filename), rawContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
