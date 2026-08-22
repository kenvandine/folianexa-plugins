package io.github.phqen1x.worldeditcraft.llm;

import io.github.phqen1x.worldeditcraft.dsl.BuildScriptValidator;
import io.github.phqen1x.worldeditcraft.dsl.OpRegistry;
import io.github.phqen1x.worldeditcraft.dsl.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void systemPromptListsEveryRegisteredOp() {
        BuildScriptValidator.GenerationLimits limits =
                new BuildScriptValidator.GenerationLimits(128, 400_000, 400, "vanilla", List.of());
        String prompt = PromptBuilder.buildSystemPrompt(limits);

        for (String opName : OpRegistry.knownNames()) {
            assertTrue(prompt.contains(opName), "prompt should mention op '" + opName + "'");
        }
    }

    @Test
    void systemPromptCapsMatchLiveConfigRatherThanConstants() {
        BuildScriptValidator.GenerationLimits limits =
                new BuildScriptValidator.GenerationLimits(64, 12345, 77, "vanilla", List.of());
        String prompt = PromptBuilder.buildSystemPrompt(limits);

        assertTrue(prompt.contains("64"));
        assertTrue(prompt.contains("12345"));
        assertTrue(prompt.contains("77"));
    }

    @Test
    void repairMessageEnumeratesOnlyRepairTriggeringIssues() {
        List<ValidationIssue> issues = List.of(
                ValidationIssue.error(4, "op msg", "Operation 4 uses palette key 'collumn', which is not defined."),
                ValidationIssue.warning(2, "clamped", "Operation 2 was clamped and should not appear in the repair message.")
        );
        String message = PromptBuilder.buildRepairMessage(issues);

        assertTrue(message.contains("collumn"));
        assertFalse(message.contains("clamped and should not appear"));
    }

    @Test
    void userMessageIncludesTheBriefAndDefaultSize() {
        String message = PromptBuilder.buildUserMessage("a ruined dwarven forge hall", new int[]{32, 24, 32});
        assertTrue(message.contains("a ruined dwarven forge hall"));
        assertTrue(message.contains("32x24x32"));
    }
}
