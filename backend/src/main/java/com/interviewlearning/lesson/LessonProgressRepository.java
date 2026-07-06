package com.interviewlearning.lesson;

import com.interviewlearning.lesson.LessonDtos.ExerciseAnswerRequest;
import com.interviewlearning.lesson.LessonDtos.SavedAnswer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores lesson progress in PostgreSQL, keyed by the STABLE exercise ids (and
 * boss-question ids), NOT by a hash of the file. Editing learning-atoms.json —
 * e.g. adding atoms — therefore keeps answers to unchanged exercises: only the
 * new exercises show up as unanswered. Unit and lesson completion are DERIVED
 * from which exercises have been answered (plus which boss questions passed),
 * so there is nothing hash-scoped to reset.
 */
@Repository
public class LessonProgressRepository {

    private final JdbcTemplate jdbc;

    public LessonProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- answers ------------------------------------------------------------

    public void recordAnswer(String topicId, ExerciseAnswerRequest req) {
        jdbc.update("""
                INSERT INTO lesson_exercise_answer
                    (topic_id, exercise_id, atom_id, unit_id, context, answer_json, correct)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (topic_id, exercise_id, context) DO UPDATE SET
                    atom_id = EXCLUDED.atom_id,
                    unit_id = EXCLUDED.unit_id,
                    answer_json = EXCLUDED.answer_json,
                    correct = EXCLUDED.correct,
                    created_at = now()
                """, topicId, req.exerciseId(), req.atomId(), req.unitId(),
                req.context() == null || req.context().isBlank() ? "lesson" : req.context(),
                req.answerJson(), req.correct());
    }

    /** All saved lesson answers for the topic (used to restore the lesson on load). */
    public List<SavedAnswer> lessonAnswers(String topicId) {
        List<SavedAnswer> out = new ArrayList<>();
        jdbc.query("""
                SELECT exercise_id, correct, answer_json
                FROM lesson_exercise_answer
                WHERE topic_id = ? AND context = 'lesson'
                """, rs -> {
            out.add(new SavedAnswer(rs.getString("exercise_id"),
                    rs.getBoolean("correct"), rs.getString("answer_json")));
        }, topicId);
        return out;
    }

    /** Exercise ids the learner has answered at least once (any correctness). */
    public Set<String> answeredExerciseIds(String topicId) {
        Set<String> ids = new HashSet<>();
        jdbc.query("""
                SELECT DISTINCT exercise_id FROM lesson_exercise_answer
                WHERE topic_id = ? AND context = 'lesson'
                """, rs -> {
            ids.add(rs.getString("exercise_id"));
        }, topicId);
        return ids;
    }

    // --- completion ---------------------------------------------------------

    public boolean isLessonCompleted(String topicId) {
        Boolean c = jdbc.query("SELECT completed FROM lesson_progress WHERE topic_id = ?",
                rs -> rs.next() ? rs.getBoolean("completed") : Boolean.FALSE, topicId);
        return Boolean.TRUE.equals(c);
    }

    /** Question ids of the currently passed boss-fight answers (live rows). */
    public Set<String> passedBossQuestionIds(String topicId) {
        Set<String> ids = new HashSet<>();
        jdbc.query("""
                SELECT question_id FROM boss_fight_answer
                WHERE topic_id = ? AND deleted_at IS NULL AND passed = TRUE
                """, rs -> {
            ids.add(rs.getString("question_id"));
        }, topicId);
        return ids;
    }

    /** A practice exercise to (re)register in the review pool on lesson completion. */
    public record PoolEntry(String exerciseId, String atomId) {
    }

    /**
     * Recomputes lesson completion from stable ids: every discovery/practice
     * exercise must have an answer AND every boss question must be passed. On
     * completion the topic's practice exercises join the global review pool.
     */
    @Transactional
    public boolean recomputeLessonCompletion(String topicId,
                                             List<String> requiredExerciseIds,
                                             List<String> bossQuestionIds,
                                             List<PoolEntry> practicePool) {
        Set<String> answered = answeredExerciseIds(topicId);
        Set<String> passedBoss = passedBossQuestionIds(topicId);
        boolean completed = !requiredExerciseIds.isEmpty()
                && answered.containsAll(requiredExerciseIds)
                && passedBoss.containsAll(bossQuestionIds);

        jdbc.update("""
                INSERT INTO lesson_progress (topic_id, completed, completed_at)
                VALUES (?, ?, CASE WHEN ? THEN now() ELSE NULL END)
                ON CONFLICT (topic_id) DO UPDATE SET
                    completed = EXCLUDED.completed,
                    completed_at = CASE
                        WHEN EXCLUDED.completed AND lesson_progress.completed_at IS NULL THEN now()
                        WHEN NOT EXCLUDED.completed THEN NULL
                        ELSE lesson_progress.completed_at END
                """, topicId, completed, completed);

        if (completed) {
            for (PoolEntry entry : practicePool) {
                jdbc.update("""
                        INSERT INTO review_pool (topic_id, exercise_id, atom_id)
                        VALUES (?, ?, ?)
                        ON CONFLICT (topic_id, exercise_id) DO UPDATE SET
                            atom_id = EXCLUDED.atom_id
                        """, topicId, entry.exerciseId(), entry.atomId());
            }
        }
        return completed;
    }
}
