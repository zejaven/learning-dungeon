package com.interviewlearning.progress;

import java.util.List;
import java.util.Map;

/** DTOs for reading and writing learner progress. */
public final class ProgressDtos {

    private ProgressDtos() {
    }

    /** The current (non-deleted) boss-fight answer for one question. */
    public record BossAnswer(
            String questionId,
            String answer,
            String verdict,
            Integer score,
            boolean passed
    ) {
    }

    /** Everything the frontend needs to restore a topic's progress on load. */
    public record TopicProgress(
            Map<String, Boolean> missions,
            Map<String, BossAnswer> bossFight,
            boolean completed
    ) {
    }

    public record MissionsRequest(List<String> missionIds) {
    }

    public record BossAnswerRequest(
            String questionId,
            String questionText,
            String answer,
            String verdict,
            Integer score,
            boolean passed
    ) {
    }

    /** Returned after saving an answer: whether the whole topic is now complete. */
    public record BossAnswerResponse(boolean topicCompleted) {
    }

    /** One saved Ask-AI question and its answer for a topic. */
    public record AssistantQa(
            long id,
            String question,
            String answer,
            String createdAt
    ) {
    }

    /** Payload to persist a freshly asked Ask-AI question and its answer. */
    public record AssistantQaRequest(
            String question,
            String answer
    ) {
    }
}
