package io.github.phqen1x.worldeditcraft.dsl;

/**
 * One problem found in a build script, carrying two messages: one for the
 * operator's chat, and one phrased as an instruction for a repair-round
 * prompt back to the model (see docs/phqen1x-rpg-suite/04-lemonade-integration.md's
 * "Getting JSON out" — specific, enumerated feedback is what makes the
 * repair loop converge instead of producing another invalid response).
 * {@code opIndex} is {@code -1} for a script-level issue (bad {@code
 * size}, malformed JSON) that isn't tied to one operation.
 */
public record ValidationIssue(Severity severity, int opIndex, String operatorMessage, String modelMessage) {

    public enum Severity {
        /** Structurally broken enough that nothing can be rasterized — always triggers a repair round. */
        FATAL,
        /** The op or field is wrong and was dropped; always triggers a repair round. */
        ERROR,
        /** Clamped or otherwise auto-corrected; reported but never triggers a repair round on its own. */
        WARNING
    }

    public static ValidationIssue fatal(int opIndex, String operatorMessage, String modelMessage) {
        return new ValidationIssue(Severity.FATAL, opIndex, operatorMessage, modelMessage);
    }

    public static ValidationIssue error(int opIndex, String operatorMessage, String modelMessage) {
        return new ValidationIssue(Severity.ERROR, opIndex, operatorMessage, modelMessage);
    }

    public static ValidationIssue warning(int opIndex, String operatorMessage, String modelMessage) {
        return new ValidationIssue(Severity.WARNING, opIndex, operatorMessage, modelMessage);
    }

    public boolean triggersRepair() {
        return severity == Severity.FATAL || severity == Severity.ERROR;
    }
}
