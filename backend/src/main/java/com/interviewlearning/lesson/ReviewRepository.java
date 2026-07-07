package com.interviewlearning.lesson;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence for the global review mode. The review list is persistent
 * per-exercise state: {@code review_pool} rows (populated on lesson completion,
 * see {@link LessonProgressRepository}) each carry a {@code pending} flag —
 * {@code true} means the exercise is currently in the review list. Answering it
 * correctly clears the flag; a wrong answer keeps it; a per-topic or global
 * "start again" sets it back. A per-topic {@code enabled} preference toggles
 * whether a topic's pending exercises take part, without losing which ones were
 * pending. There is no session snapshot — ordering and requeue-on-wrong are a
 * client concern over this live set.
 */
@Repository
public class ReviewRepository {

    /** One review_pool row (an exercise of a fully completed lesson). */
    public record PoolRow(long id, String topicId, String exerciseId, String atomId, boolean pending) {
    }

    private final JdbcTemplate jdbc;

    public ReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PoolRow> pool() {
        List<PoolRow> out = new ArrayList<>();
        jdbc.query("""
                SELECT id, topic_id, exercise_id, atom_id, pending
                FROM review_pool
                ORDER BY topic_id, exercise_id
                """, rs -> {
            out.add(new PoolRow(rs.getLong("id"), rs.getString("topic_id"),
                    rs.getString("exercise_id"), rs.getString("atom_id"),
                    rs.getBoolean("pending")));
        });
        return out;
    }

    /** Removes rows whose exercise no longer exists in the topic's current atoms file. */
    public void deletePoolRows(List<Long> ids) {
        for (Long id : ids) {
            jdbc.update("DELETE FROM review_pool WHERE id = ?", id);
        }
    }

    /**
     * Records a review answer: bumps stats and, on a correct answer, drops the
     * exercise from the list ({@code pending = FALSE}). A wrong answer leaves it
     * pending so it resurfaces.
     */
    public void recordAnswer(String topicId, String exerciseId, boolean correct) {
        jdbc.update("""
                UPDATE review_pool SET
                    last_reviewed_at = now(),
                    last_correct = ?,
                    correct_count = correct_count + CASE WHEN ? THEN 1 ELSE 0 END,
                    wrong_count = wrong_count + CASE WHEN ? THEN 0 ELSE 1 END,
                    pending = CASE WHEN ? THEN FALSE ELSE pending END
                WHERE topic_id = ? AND exercise_id = ?
                """, correct, correct, correct, correct, topicId, exerciseId);
    }

    /** Returns all of a topic's answered exercises to the review list. */
    public void restartTopic(String topicId) {
        jdbc.update("UPDATE review_pool SET pending = TRUE WHERE topic_id = ?", topicId);
    }

    /** Returns every answered exercise, across all topics, to the review list. */
    public void restartAll() {
        jdbc.update("UPDATE review_pool SET pending = TRUE");
    }

    /** Per-topic review preference (topic_id -> enabled); absent topics default to enabled. */
    public Map<String, Boolean> topicPrefs() {
        Map<String, Boolean> out = new HashMap<>();
        jdbc.query("SELECT topic_id, enabled FROM review_topic_pref", rs -> {
            out.put(rs.getString("topic_id"), rs.getBoolean("enabled"));
        });
        return out;
    }

    /** Upserts whether a topic's pooled exercises take part in review sessions. */
    public void setTopicEnabled(String topicId, boolean enabled) {
        jdbc.update("""
                INSERT INTO review_topic_pref (topic_id, enabled) VALUES (?, ?)
                ON CONFLICT (topic_id) DO UPDATE SET enabled = EXCLUDED.enabled
                """, topicId, enabled);
    }
}
