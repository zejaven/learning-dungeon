package com.interviewlearning.codetask;

import com.interviewlearning.codetask.CodeTaskDtos.TaskProgress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistence for code tasks. The server runs the tests, so unlike lesson
 * answers the verdict is its own and not reported by the client. A solved task
 * stays solved whatever later runs do.
 */
@Repository
public class CodeTaskProgressRepository {

    private final JdbcTemplate jdbc;

    public CodeTaskProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, TaskProgress> forTopic(String topicId) {
        Map<String, TaskProgress> progress = new HashMap<>();
        jdbc.query("SELECT task_id, attempts, solved_at IS NOT NULL AS solved "
                        + "FROM code_task_progress WHERE topic_id = ?",
                rs -> {
                    progress.put(rs.getString("task_id"),
                            new TaskProgress(rs.getInt("attempts"), rs.getBoolean("solved")));
                },
                topicId);
        return progress;
    }

    /** Counts a run that reached the tests. */
    public TaskProgress recordRun(String topicId, String taskId, boolean solved) {
        return jdbc.queryForObject("""
                INSERT INTO code_task_progress (topic_id, task_id, attempts, solved_at)
                VALUES (?, ?, 1, CASE WHEN ? THEN now() END)
                ON CONFLICT (topic_id, task_id) DO UPDATE SET
                    attempts  = code_task_progress.attempts + 1,
                    solved_at = COALESCE(code_task_progress.solved_at, EXCLUDED.solved_at)
                RETURNING attempts, solved_at IS NOT NULL AS solved
                """,
                (rs, n) -> new TaskProgress(rs.getInt("attempts"), rs.getBoolean("solved")),
                topicId, taskId, solved);
    }
}
