package com.interviewlearning.lesson;

import com.interviewlearning.lesson.LessonDtos.ExerciseAnswerRequest;
import com.interviewlearning.lesson.LessonDtos.SavedAnswer;
import com.interviewlearning.lesson.LessonUnits.UnitRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores lesson progress in PostgreSQL. All discovery/practice progress is
 * scoped by the sha-256 of learning-atoms.json, so regenerating the file resets
 * the lesson by construction while old rows stay as history. Boss progress
 * lives in {@code boss_fight_answer} (stable quiz.yaml question ids) and is
 * deliberately NOT hash-scoped.
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
                    (topic_id, exercise_id, atom_id, unit_id, context, atoms_hash, answer_json, correct)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (topic_id, exercise_id, context) DO UPDATE SET
                    atom_id = EXCLUDED.atom_id,
                    unit_id = EXCLUDED.unit_id,
                    atoms_hash = EXCLUDED.atoms_hash,
                    answer_json = EXCLUDED.answer_json,
                    correct = EXCLUDED.correct,
                    created_at = now()
                """, topicId, req.exerciseId(), req.atomId(), req.unitId(),
                req.context() == null || req.context().isBlank() ? "lesson" : req.context(),
                req.atomsHash() == null ? "" : req.atomsHash(),
                req.answerJson(), req.correct());
    }

    /** Saved lesson answers for the current atoms generation (for restoring on load). */
    public List<SavedAnswer> lessonAnswers(String topicId, String atomsHash) {
        List<SavedAnswer> out = new ArrayList<>();
        jdbc.query("""
                SELECT exercise_id, correct, answer_json
                FROM lesson_exercise_answer
                WHERE topic_id = ? AND context = 'lesson' AND atoms_hash = ?
                """, rs -> {
            out.add(new SavedAnswer(rs.getString("exercise_id"),
                    rs.getBoolean("correct"), rs.getString("answer_json")));
        }, topicId, atomsHash);
        return out;
    }

    // --- unit progress ------------------------------------------------------

    public List<String> completedUnits(String topicId, String atomsHash) {
        List<String> out = new ArrayList<>();
        jdbc.query("""
                SELECT unit_id FROM lesson_unit_progress
                WHERE topic_id = ? AND atoms_hash = ?
                ORDER BY completed_at, id
                """, rs -> {
            out.add(rs.getString("unit_id"));
        }, topicId, atomsHash);
        return out;
    }

    public void completeUnit(String topicId, String unitId, String atomsHash) {
        jdbc.update("""
                INSERT INTO lesson_unit_progress (topic_id, unit_id, atoms_hash)
                VALUES (?, ?, ?)
                ON CONFLICT (topic_id, unit_id, atoms_hash) DO NOTHING
                """, topicId, unitId, atomsHash);
    }

    // --- lesson completion --------------------------------------------------

    public boolean isLessonCompleted(String topicId, String atomsHash) {
        Boolean c = jdbc.query("""
                SELECT completed FROM lesson_progress
                WHERE topic_id = ? AND atoms_hash = ?
                """, rs -> rs.next() ? rs.getBoolean("completed") : Boolean.FALSE, topicId, atomsHash);
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
     * Recomputes lesson completion for the current atoms generation: every
     * non-boss unit must be completed for this hash AND every boss question
     * passed. On completion the topic's practice exercises join the global
     * review pool (explicit rows; re-completion after a regeneration refreshes
     * atom ids and hash but keeps the accumulated stats).
     */
    @Transactional
    public boolean recomputeLessonCompletion(String topicId, String atomsHash,
                                             List<UnitRef> units, List<PoolEntry> practicePool) {
        Set<String> done = new HashSet<>(completedUnits(topicId, atomsHash));
        Set<String> passedBoss = passedBossQuestionIds(topicId);
        boolean completed = !units.isEmpty() && units.stream().allMatch(u ->
                "boss".equals(u.kind())
                        ? passedBoss.contains(u.unitId().substring("b:".length()))
                        : done.contains(u.unitId()));

        jdbc.update("""
                INSERT INTO lesson_progress (topic_id, atoms_hash, completed, completed_at)
                VALUES (?, ?, ?, CASE WHEN ? THEN now() ELSE NULL END)
                ON CONFLICT (topic_id) DO UPDATE SET
                    atoms_hash = EXCLUDED.atoms_hash,
                    completed = EXCLUDED.completed,
                    completed_at = CASE
                        WHEN EXCLUDED.completed AND lesson_progress.completed_at IS NULL THEN now()
                        WHEN NOT EXCLUDED.completed THEN NULL
                        ELSE lesson_progress.completed_at END
                """, topicId, atomsHash, completed, completed);

        if (completed) {
            for (PoolEntry entry : practicePool) {
                jdbc.update("""
                        INSERT INTO review_pool (topic_id, exercise_id, atom_id, atoms_hash)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (topic_id, exercise_id) DO UPDATE SET
                            atom_id = EXCLUDED.atom_id,
                            atoms_hash = EXCLUDED.atoms_hash
                        """, topicId, entry.exerciseId(), entry.atomId(), atomsHash);
            }
        }
        return completed;
    }
}
