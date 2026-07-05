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

    /** One knowledge atom: a single idea with its discovery and practice exercises. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Atom(
            String id,
            Localized title,
            Localized summary,
            List<Exercise> discovery,
            List<Exercise> practice
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
            Feedback feedback,
            List<Option> options,
            Boolean answer,
            Localized text,
            LocalizedList answers,
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
     * Atoms plus the sha-256 of the file bytes. The hash scopes all lesson
     * progress: regenerating the file changes the hash and thereby resets
     * discovery/practice progress without touching history rows.
     */
    public record AtomsResponse(String atomsHash, LearningAtoms atoms) {
    }

    /** Lesson progress for the current atoms file (older hashes report as empty). */
    public record LessonState(String atomsHash, List<String> completedUnits, boolean lessonCompleted,
                              List<SavedAnswer> answers) {
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
            String atomsHash,
            String answerJson,
            boolean correct
    ) {
    }

    public record UnitCompleteRequest(String unitId, String atomsHash) {
    }

    public record UnitCompleteResponse(boolean lessonCompleted) {
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
