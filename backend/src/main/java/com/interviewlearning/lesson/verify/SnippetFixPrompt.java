package com.interviewlearning.lesson.verify;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.List;

/**
 * The prompt of the one repair round after a lesson generation: the exercises
 * whose real output matched another option than the marked one. It stands on
 * its own (no theory, no contract) because it only edits a file the AI has just
 * written, which keeps the round cheap.
 */
public final class SnippetFixPrompt {

    /** An exercise the run contradicted, with what the run showed. */
    public record Item(JsonNode exercise, SnippetCheck.Verdict verdict) {
    }

    private SnippetFixPrompt() {
    }

    public static String build(Path atomsFile, List<Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("You have just written the lesson file below. The app then checked its predict_output ")
                .append("exercises by RUNNING their code on JDK 21: each snippet was wrapped into a class Main ")
                .append("(imports java.util.*, java.util.function.*, java.util.stream.*, java.util.concurrent.*, ")
                .append("java.util.concurrent.atomic.*; declared classes/records beside Main, declared methods ")
                .append("as static methods of Main, every other line inside main), compiled and run twice.\n\n")
                .append("For the exercises listed below, the real output matches a DIFFERENT option than the one ")
                .append("marked correct, so as written they teach something false. For each one:\n")
                .append("- decide whether the answer key or the snippet is wrong;\n")
                .append("- wrong key: move `correct` to the right option and make the option texts, their ")
                .append("`feedback`, the exercise `feedback` and the `reveal` (in every language) agree with the ")
                .append("real behaviour;\n")
                .append("- wrong snippet (the marked answer is the point of the atom, the code just does not ")
                .append("show it): change the code so that its real output is the marked option;\n")
                .append("- if you are certain the run is misleading because the wrapping changed what the code ")
                .append("means, leave the exercise as it is and say why in one line.\n\n")
                .append("Rules: edit the file in place and keep it valid JSON of the same shape. Keep EVERY id ")
                .append("(atoms, exercises, options) unchanged, keep exactly one option marked correct, and do ")
                .append("not touch any exercise that is not listed here.\n\n")
                .append("FILE: ").append(atomsFile.toAbsolutePath()).append("\n");
        for (Item item : items) {
            JsonNode ex = item.exercise();
            sb.append("\n---\n\nEXERCISE ").append(ex.path("id").asText()).append("\n")
                    .append("Question: ").append(ex.path("prompt").path("en").asText()).append("\n")
                    .append("Code:\n```java\n").append(ex.path("code").asText()).append("\n```\n")
                    .append("Options:\n");
            for (JsonNode o : ex.path("options")) {
                sb.append("- ").append(o.path("id").asText()).append(") ")
                        .append(o.path("text").path("en").asText())
                        .append(o.path("correct").asBoolean() ? "   <- MARKED CORRECT" : "")
                        .append("\n");
            }
            sb.append("Real output of the run:\n```\n").append(item.verdict().output()).append("\n```\n")
                    .append("It matches option(s): ").append(String.join(", ", item.verdict().literalMatches()))
                    .append("\n");
        }
        return sb.toString();
    }
}
