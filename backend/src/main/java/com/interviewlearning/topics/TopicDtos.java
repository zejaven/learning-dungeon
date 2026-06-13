package com.interviewlearning.topics;

import java.util.List;

/** Data transfer objects for topics served to the frontend. */
public final class TopicDtos {

    private TopicDtos() {
    }

    /** Lightweight entry for the topic switcher. */
    public record TopicSummary(
            String id,
            String title,
            String category,
            String type,
            String summary
    ) {
    }

    public record Example(
            String id,
            String title,
            String code,
            String explanation
    ) {
    }

    /**
     * A mission/challenge. {@code event} is the trace event type whose presence
     * in a run satisfies the mission (checked on the frontend).
     */
    public record Mission(
            String id,
            String title,
            String goal,
            String event
    ) {
    }

    /** Full payload for a single topic. */
    public record TopicDetail(
            String id,
            String title,
            String category,
            String type,
            String summary,
            List<String> primitives,
            String explanation,
            List<Example> examples,
            String defaultExampleId,
            List<Mission> missions
    ) {
    }
}
