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
            boolean completed
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
     * A mission/challenge. {@code event} is the trace event type whose presence
     * in a run satisfies the mission (checked on the frontend).
     */
    public record Mission(
            String id,
            Localized title,
            Localized goal,
            String event
    ) {
    }

    /** Full payload for a single topic. */
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
            List<Localized> bossFight
    ) {
    }
}
