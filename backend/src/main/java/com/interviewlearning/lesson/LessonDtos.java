package com.interviewlearning.lesson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.interviewlearning.topics.TopicDtos.Localized;

import java.util.List;

/** Data transfer objects for the "Learn by micro-actions" lesson mode. */
public final class LessonDtos {

    private LessonDtos() {
    }

    /**
     * The whole {@code topics/<id>/learning-atoms.json} file. Atoms are ordered:
     * later atoms may build on earlier ones, and unit derivation follows file
     * order (see {@link LessonUnits}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LearningAtoms(
            int schemaVersion,
            String topicId,
            int sourceVersion,
            String aiProvider,
            String aiModel,
            List<Atom> atoms
    ) {
    }

    /**
     * One knowledge atom: a single idea with its discovery and practice
     * exercises. A {@code capstone} atom instead synthesizes across atoms; its
     * practice is rendered as a dedicated final block before the Boss Fight.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Atom(
            String id,
            Localized title,
            Localized summary,
            List<Exercise> discovery,
            List<Exercise> practice,
            boolean capstone
    ) {
    }

    /**
     * One micro-exercise. {@code type} discriminates which of the optional
     * payload fields apply:
     * <ul>
     *   <li>{@code multiple_choice} / {@code predict_output} / {@code spot_bug}: {@code options}</li>
     *   <li>{@code true_false}: {@code answer}</li>
     *   <li>{@code fill_blank}: {@code text} (with one {@code ___}) + {@code answers}</li>
     *   <li>{@code word_bank}: {@code tokens} (correct order) + {@code distractors}</li>
     *   <li>{@code sort_steps}: {@code steps} (correct order)</li>
     *   <li>{@code match_pairs}: {@code pairs}</li>
     * </ul>
     * Grading is deterministic and happens on the frontend; the backend only
     * stores results. Kept as one record (not a sealed hierarchy) so Jackson
     * mapping stays trivial and the schema can evolve in the JSON file.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Exercise(
            String id,
            String type,
            Localized prompt,
            String code,
            String codeLang,
            Localized mermaid,
            /**
             * Optional teaching card shown AFTER the learner answers (feedback
             * phase). Markdown, so it may embed a fenced ```mermaid``` diagram,
             * code, and cross-links. This is where the concept is actually
             * taught; {@code feedback} stays a short verdict.
             */
            Localized reveal,
            Feedback feedback,
            List<Option> options,
            Boolean answer,
            Localized text,
            /** Legacy single-blank accepted answers; use {@code blanks} for one or more blanks. */
            LocalizedList answers,
            /** Accepted answers per {@code ___} in order (fill_blank); supports multiple blanks. */
            List<LocalizedList> blanks,
            LocalizedList tokens,
            LocalizedList distractors,
            List<Step> steps,
            List<Pair> pairs
    ) {
    }

    /** Post-answer explanation; each side is at most 1-2 sentences of theory. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feedback(Localized correct, Localized incorrect) {
    }

    /** One choice of a multiple-choice-style exercise; wrong options carry misconception feedback. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String id, Localized text, boolean correct, Localized feedback) {
    }

    /** A per-language list of strings (fill-blank answers, word-bank tokens). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalizedList(List<String> en, List<String> ru) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String id, Localized text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pair(String id, Localized left, Localized right) {
    }

    /**
     * Lesson progress. Unit/lesson completion is derived on the client from
     * {@code answers} (keyed by stable exercise ids), so edits to other atoms
     * never wipe it.
     */
    public record LessonState(boolean lessonCompleted, List<SavedAnswer> answers) {
    }

    /** One saved answer, for restoring the lesson when the learner revisits a unit. */
    public record SavedAnswer(String exerciseId, boolean correct, String answerJson) {
    }

    /** One answered exercise; {@code context} is {@code lesson} or {@code review}. */
    public record ExerciseAnswerRequest(
            String exerciseId,
            String atomId,
            String unitId,
            String context,
            String answerJson,
            boolean correct
    ) {
    }

    public record RecomputeResponse(boolean lessonCompleted) {
    }

    /** Home-screen badge payload for the global review mode. */
    public record ReviewSummary(int poolSize, int topicCount) {
    }

    /** One review-pool exercise, self-contained so the review screen never loads topics. */
    public record ReviewItem(String topicId, Localized topicTitle, String atomId, Exercise exercise) {
    }

    /**
     * A running review session. {@code queue} holds indexes into {@code items};
     * wrong answers push their index back onto the tail, {@code position} is the
     * cursor into {@code queue}.
     */
    public record ReviewSessionDto(
            long sessionId,
            List<ReviewItem> items,
            List<Integer> queue,
            int position,
            boolean finished
    ) {
    }

    public record ReviewAnswerRequest(int itemIndex, boolean correct, String answerJson) {
    }

    public record ReviewAnswerResponse(List<Integer> queue, int position, boolean finished) {
    }
}
