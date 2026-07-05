package com.interviewlearning.topics;

import java.util.List;

/** Data transfer objects for topics served to the frontend. */
public final class TopicDtos {

    private TopicDtos() {
    }

    /** A piece of text in both supported languages; the frontend picks one. */
    public record Localized(String en, String ru) {
        public static Localized of(String value) {
            return new Localized(value, value);
        }
    }

    /** Lightweight entry for the topic switcher. */
    public record TopicSummary(
            String id,
            Localized title,
            Localized category,
            String type,
            Localized summary,
            boolean completed,
            String categoryId,
            String categoryName,
            int difficulty,
            String catalogId,
            String mode,
            String aiProvider,
            String aiModel,
            boolean hasAtoms
    ) {
    }

    public record Example(
            String id,
            Localized title,
            String code,
            Localized explanation
    ) {
    }

    /**
     * A mission/challenge. For a trace topic ({@code type == "event"}, the
     * default) {@code event} is the trace event type whose presence in a run
     * satisfies it. For a structural topic ({@code type == "structure"})
     * {@code requires} holds predicates evaluated against the analyzed class
     * graph (checked on the frontend). Predicates are passed through as raw
     * maps so the rule schema can evolve without backend changes.
     */
    public record Mission(
            String id,
            Localized title,
            Localized goal,
            String event,
            String type,
            List<Object> requires
    ) {
    }

    /** One file of a structural topic's starter project (path relative to the topic root). */
    public record ProjectFile(
            String path,
            String content
    ) {
    }

    /**
     * One boss-fight interview question. {@code id} is stable across edits so a
     * saved answer stays attached to the right question even if questions are
     * reordered or inserted.
     */
    public record BossQuestion(
            String id,
            Localized text
    ) {
    }

    /**
     * Full payload for a single topic. {@code mode} is {@code "trace"} (the
     * default behavioural engine: examples + visualizer + trace events) or
     * {@code "structural"} (a multi-file project analyzed into a class graph;
     * {@code starterFiles} seeds the editor, {@code examples}/visualizer unused).
     */
    public record TopicDetail(
            String id,
            Localized title,
            Localized category,
            String type,
            Localized summary,
            List<String> primitives,
            Localized explanation,
            List<Example> examples,
            String defaultExampleId,
            List<Mission> missions,
            Localized assistantExample,
            List<BossQuestion> bossFight,
            String mode,
            List<ProjectFile> starterFiles,
            String style,
            String aiProvider,
            String aiModel,
            boolean hasAtoms
    ) {
    }
}
