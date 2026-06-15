package com.interviewlearning.progress;

import com.interviewlearning.progress.ProgressDtos.BossAnswer;
import com.interviewlearning.progress.ProgressDtos.BossAnswerRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores and reads learner progress in PostgreSQL. Boss-fight answers are
 * versioned: the live answer is the row with {@code deleted_at IS NULL}, and
 * re-answering soft-deletes the previous one so history is never lost.
 */
@Repository
public class ProgressRepository {

    private final JdbcTemplate jdbc;

    public ProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- Missions ---------------------------------------------------------

    public Map<String, Boolean> missions(String topicId) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        jdbc.query(
                "SELECT mission_id, completed FROM mission_progress WHERE topic_id = ?",
                rs -> {
                    result.put(rs.getString("mission_id"), rs.getBoolean("completed"));
                },
                topicId);
        return result;
    }

    public void completeMissions(String topicId, List<String> missionIds) {
        for (String missionId : missionIds) {
            jdbc.update("""
                    INSERT INTO mission_progress (topic_id, mission_id, completed, completed_at)
                    VALUES (?, ?, TRUE, now())
                    ON CONFLICT (topic_id, mission_id)
                    DO UPDATE SET completed = TRUE,
                                  completed_at = COALESCE(mission_progress.completed_at, now())
                    """, topicId, missionId);
        }
    }

    // --- Boss fight -------------------------------------------------------

    public Map<String, BossAnswer> currentBossAnswers(String topicId) {
        Map<String, BossAnswer> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT question_id, answer, verdict, score, passed
                FROM boss_fight_answer
                WHERE topic_id = ? AND deleted_at IS NULL
                ORDER BY question_id
                """, rs -> {
            String qid = rs.getString("question_id");
            Integer score = rs.getObject("score") == null ? null : rs.getInt("score");
            result.put(qid, new BossAnswer(qid, rs.getString("answer"),
                    rs.getString("verdict"), score, rs.getBoolean("passed")));
        }, topicId);
        return result;
    }

    /** Soft-deletes the current answer for the question, then inserts the new one. */
    @Transactional
    public void saveBossAnswer(String topicId, BossAnswerRequest req) {
        jdbc.update("""
                UPDATE boss_fight_answer SET deleted_at = now()
                WHERE topic_id = ? AND question_id = ? AND deleted_at IS NULL
                """, topicId, req.questionId());
        jdbc.update("""
                INSERT INTO boss_fight_answer
                    (topic_id, question_id, question_text, answer, verdict, score, passed)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, topicId, req.questionId(), req.questionText(), req.answer(),
                req.verdict(), req.score(), req.passed());
    }

    private int countPassed(String topicId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM boss_fight_answer
                WHERE topic_id = ? AND deleted_at IS NULL AND passed = TRUE
                """, Integer.class, topicId);
        return n == null ? 0 : n;
    }

    // --- Topic completion -------------------------------------------------

    /** Marks the topic complete iff every boss-fight question is currently passed. */
    public boolean recomputeCompletion(String topicId, int totalQuestions) {
        boolean completed = totalQuestions > 0 && countPassed(topicId) >= totalQuestions;
        jdbc.update("""
                INSERT INTO topic_progress (topic_id, completed, completed_at)
                VALUES (?, ?, CASE WHEN ? THEN now() ELSE NULL END)
                ON CONFLICT (topic_id) DO UPDATE SET
                    completed = EXCLUDED.completed,
                    completed_at = CASE
                        WHEN EXCLUDED.completed AND topic_progress.completed_at IS NULL THEN now()
                        WHEN NOT EXCLUDED.completed THEN NULL
                        ELSE topic_progress.completed_at END
                """, topicId, completed, completed);
        return completed;
    }

    public boolean isCompleted(String topicId) {
        Boolean c = jdbc.query(
                "SELECT completed FROM topic_progress WHERE topic_id = ?",
                rs -> rs.next() ? rs.getBoolean("completed") : Boolean.FALSE,
                topicId);
        return Boolean.TRUE.equals(c);
    }

    public Set<String> completedTopicIds() {
        Set<String> ids = new HashSet<>();
        jdbc.query("SELECT topic_id FROM topic_progress WHERE completed = TRUE",
                rs -> {
                    ids.add(rs.getString("topic_id"));
                });
        return ids;
    }
}
