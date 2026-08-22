package io.github.phqen1x.worldeditcraft.llm;

import io.github.phqen1x.worldeditcraft.dsl.BuildScriptValidator;
import io.github.phqen1x.worldeditcraft.dsl.OpRegistry;
import io.github.phqen1x.worldeditcraft.dsl.ValidationIssue;

import java.util.List;

/**
 * Builds the system prompt for structure generation from {@link
 * OpRegistry} — never hand-written, so adding an op to the registry adds
 * it to the prompt in the same edit that adds it to the parser and
 * validator (see docs/phqen1x-rpg-suite/03-buildscript-dsl.md's
 * "Prompting"). Also builds the repair-round message from accumulated
 * {@link ValidationIssue}s, in the specific, enumerated shape the design
 * doc's "Getting JSON out" §4 shows converges faster than vague feedback.
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    public static String buildSystemPrompt(BuildScriptValidator.GenerationLimits limits) {
        StringBuilder sb = new StringBuilder();
        sb.append("You generate Minecraft structures as a single JSON build script. ");
        sb.append("A build script is a JSON object with fields: name (string), size ([width, height, length] ")
                .append("integers), seed (integer, optional), palette (object mapping short keys to Minecraft ")
                .append("block-state strings), and ops (an array of operations applied in order).\n\n");

        sb.append("Example:\n");
        sb.append("{\"name\": \"wayside_shrine\", \"size\": [9, 7, 9], \"seed\": 4412, ")
                .append("\"palette\": {\"base\": \"minecraft:stone_bricks\"}, ")
                .append("\"ops\": [{\"op\": \"box\", \"from\": [0,0,0], \"to\": [8,0,8], \"block\": \"base\"}, ")
                .append("{\"op\": \"marker\", \"at\": [4,1,4], \"id\": \"entrance\"}]}\n\n");

        sb.append("Available operations:\n");
        for (OpRegistry.OpSpec spec : OpRegistry.all()) {
            sb.append("- ").append(spec.name()).append(" (requires: ")
                    .append(String.join(", ", spec.requiredFields())).append("): ")
                    .append(spec.promptDescription()).append('\n');
        }
        sb.append("\nEvery operation may also carry: repeat ({\"count\": n, \"step\": [dx,dy,dz]}, count <= 64), ")
                .append("replace_only (a palette key or block string), skip_air (boolean), and chance (0.0-1.0).\n\n");

        sb.append("Hard limits for this request: max dimension per axis is ").append(limits.maxDimension())
                .append(", max volume (width*height*length) is ").append(limits.maxVolume())
                .append(", max number of operations is ").append(limits.maxOps()).append(".\n\n");

        sb.append("Prefer bulk operations and 'repeat' over repeating yourself. Use noise_replace, scatter, ")
                .append("and carve so the structure doesn't look extruded by a machine. Place markers ")
                .append("(entrance, spawn, boss, loot, npc, mob, focus) where appropriate.\n\n");

        sb.append("Respond with the JSON object and nothing else — no explanation, no markdown code fence, ")
                .append("no preamble or follow-up text.");
        return sb.toString();
    }

    public static String buildUserMessage(String brief, int[] defaultSize) {
        return "Generate a build script for: " + brief.strip()
                + ". If no size is implied, use approximately " + defaultSize[0] + "x" + defaultSize[1] + "x" + defaultSize[2] + ".";
    }

    /** The repair-round message appended after a failed attempt — see the design doc's worked example. */
    public static String buildRepairMessage(List<ValidationIssue> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your previous response had problems. Fix them and return the corrected complete JSON object.\n\n");
        for (ValidationIssue issue : issues) {
            if (!issue.triggersRepair()) {
                continue;
            }
            sb.append("- ").append(issue.modelMessage()).append('\n');
        }
        sb.append("\nReturn only the corrected JSON object.");
        return sb.toString();
    }
}
