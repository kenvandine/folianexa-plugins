package io.github.phqen1x.worldeditcraft;

import io.github.phqen1x.worldeditcraft.dsl.BuildScriptValidator;
import io.github.phqen1x.worldeditcraft.llm.LemonadeSettings;

import java.util.List;

/**
 * Immutable record tree of the whole of {@code config.yml}. Built by
 * {@link ConfigLoader}; nothing here reads a {@link
 * org.bukkit.configuration.file.FileConfiguration} directly, so this
 * class (and everything it's built from) has no {@code org.bukkit}
 * import and is trivially unit-testable.
 */
public record WorldEditCraftConfig(
        LemonadeSettings lemonade,
        Generation generation,
        Paste paste,
        Library library,
        WorldEdit worldedit
) {
    public record Generation(
            int maxDimension,
            long maxVolume,
            int maxOps,
            int[] defaultSize,
            String blockPolicy,
            List<String> blockAllowlist,
            boolean keepFailedResponses
    ) {
        public Generation {
            defaultSize = defaultSize.clone();
            blockAllowlist = List.copyOf(blockAllowlist);
        }

        @Override
        public int[] defaultSize() {
            return defaultSize.clone();
        }

        public BuildScriptValidator.GenerationLimits toLimits() {
            return new BuildScriptValidator.GenerationLimits(maxDimension, maxVolume, maxOps, blockPolicy, blockAllowlist);
        }
    }

    public record Paste(
            int blocksPerTick,
            boolean clearVolumeFirst,
            String unknownBlock,
            int undoHistory,
            int maxConcurrentJobs,
            int previewSeconds
    ) {
    }

    public record Library(String directory, int maxEntries) {
    }

    public record WorldEdit(boolean delegatePaste) {
    }
}
