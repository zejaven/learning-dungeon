package com.interviewlearning.lesson;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for the global review mode: the explicit exercise pool
 * (populated on lesson completion, see {@link LessonProgressRepository}) and
 * the single active review session whose queue/requeue state survives reloads.
 */
@Repository
public class ReviewRepository {

    /** One review_pool row (an exercise of a fully completed lesson). */
    public record PoolRow(long id, String topicId, String exerciseId, String atomId) {
    }

    /** The active review session row. */
    public record SessionRow(long id, String stateJson) {
    }

    private final JdbcTemplate jdbc;

    public ReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PoolRow> pool() {
        List<PoolRow> out = new ArrayList<>();
        jdbc.query("""
                SELECT id, topic_id, exercise_id, atom_id
                FROM review_pool
                ORDER BY topic_id, exercise_id
                """, rs -> {
            out.add(new PoolRow(rs.getLong("id"), rs.getString("topic_id"),
                    rs.getString("exercise_id"), rs.getString("atom_id")));
        });
        return out;
    }

    /** Removes rows whose exercise no longer exists in the topic's current atoms file. */
    public void deletePoolRows(List<Long> ids) {
        for (Long id : ids) {
            jdbc.update("DELETE FROM review_pool WHERE id = ?", id);
        }
    }

    public void bumpStats(String topicId, String exerciseId, boolean correct) {
        jdbc.update("""
                UPDATE review_pool SET
                    last_reviewed_at = now(),
                    last_correct = ?,
                    correct_count = correct_count + CASE WHEN ? THEN 1 ELSE 0 END,
                    wrong_count = wrong_count + CASE WHEN ? THEN 0 ELSE 1 END
                WHERE topic_id = ? AND exercise_id = ?
                """, correct, correct, correct, topicId, exerciseId);
    }

    public Optional<SessionRow> activeSession() {
        List<SessionRow> rows = jdbc.query("""
                SELECT id, state_json FROM review_session
                WHERE finished_at IS NULL
                ORDER BY id DESC LIMIT 1
                """, (rs, i) -> new SessionRow(rs.getLong("id"), rs.getString("state_json")));
        return rows.stream().findFirst();
    }

    public Optional<SessionRow> session(long id) {
        List<SessionRow> rows = jdbc.query(
                "SELECT id, state_json FROM review_session WHERE id = ?",
                (rs, i) -> new SessionRow(rs.getLong("id"), rs.getString("state_json")), id);
        return rows.stream().findFirst();
    }

    public long insertSession(String stateJson) {
        Long id = jdbc.queryForObject(
                "INSERT INTO review_session (state_json) VALUES (?) RETURNING id",
                Long.class, stateJson);
        return id == null ? 0 : id;
    }

    public void updateSessionState(long id, String stateJson) {
        jdbc.update("UPDATE review_session SET state_json = ? WHERE id = ?", stateJson, id);
    }

    public void finishSession(long id) {
        jdbc.update("UPDATE review_session SET finished_at = now() WHERE id = ? AND finished_at IS NULL", id);
    }
}
