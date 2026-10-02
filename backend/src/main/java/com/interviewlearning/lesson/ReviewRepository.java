package com.interviewlearning.lesson;

import com.interviewlearning.lesson.ReviewSchedule.Next;
import com.interviewlearning.lesson.ReviewSchedule.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence for the global review mode. A {@code review_pool} row (created by
 * {@link #enroll} the moment a practice exercise is first answered in a lesson)
 * carries a spaced-repetition position: {@code srs_step} is the rung of
 * {@link ReviewSchedule}'s ladder it is about to be served at and {@code due_at}
 * is when that happens. Whether it is actually offered is derived from those two
 * against the current time, never stored — see {@link ReviewSchedule#stateOf}.
 *
 * <p>A per-topic {@code enabled} preference toggles whether a topic takes part,
 * without touching its schedule. There is no session snapshot: ordering,
 * requeue-on-wrong and the per-session cap are client concerns over this set.
 */
@Repository
public class ReviewRepository {

    private static final Logger log = LoggerFactory.getLogger(ReviewRepository.class);

    /** How far back a client-supplied answer time may reach before it is distrusted. */
    private static final Duration MAX_BACKDATE = Duration.ofDays(30);

    /** One review_pool row (a practice exercise answered at least once in a lesson). */
    public record PoolRow(long id, String topicId, String exerciseId, String atomId,
                          int srsStep, Instant dueAt, Instant graduatedAt, int lapseCount) {
    }

    private final JdbcTemplate jdbc;
    private final double speed;

    public ReviewRepository(JdbcTemplate jdbc, @Value("${app.review.speed:1.0}") double speed) {
        this.jdbc = jdbc;
        this.speed = speed;
        if (speed != 1.0) {
            log.warn("app.review.speed={} — repetition intervals are compressed. This is a "
                    + "testing knob: every answer recorded while it is set schedules real rows "
                    + "at fake times, so reset the schedule before using the app for real.", speed);
        }
    }

    /** The interval multiplier in force; callers need it to derive window/expiry. */
    public double speed() {
        return speed;
    }

    /**
     * Every pool row, graduated ones included. Filtering by due date happens on
     * the client so that the response is invariant over time — that is what lets
     * the PWA cache it, lets the per-domain filter and the session cap apply
     * after the fact, and makes offline review honest about what is due.
     */
    public List<PoolRow> pool() {
        List<PoolRow> out = new ArrayList<>();
        jdbc.query("""
                SELECT id, topic_id, exercise_id, atom_id, srs_step, due_at, graduated_at, lapse_count
                FROM review_pool
                ORDER BY topic_id, exercise_id
                """, rs -> {
            out.add(new PoolRow(rs.getLong("id"), rs.getString("topic_id"),
                    rs.getString("exercise_id"), rs.getString("atom_id"),
                    rs.getInt("srs_step"), instant(rs.getTimestamp("due_at")),
                    instant(rs.getTimestamp("graduated_at")), rs.getInt("lapse_count")));
        });
        return out;
    }

    /**
     * Puts a practice exercise on the repetition ladder because it was just
     * answered in a lesson, first due one interval after {@code answeredAt}.
     *
     * <p>A no-op for an exercise that is already enrolled: revisiting a lesson
     * unit, the mistakes loop, or a re-answer after regeneration all land here
     * again, and touching the schedule columns would knock a mastered exercise
     * back onto the first rung. Only {@code atom_id} follows the current file.
     *
     * @return whether a new row was created
     */
    public boolean enroll(String topicId, String exerciseId, String atomId, Instant answeredAt) {
        Instant at = trustedAnswerTime(answeredAt);
        int inserted = jdbc.update("""
                INSERT INTO review_pool (topic_id, exercise_id, atom_id, added_at, due_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (topic_id, exercise_id) DO NOTHING
                """, topicId, exerciseId, atomId, Timestamp.from(at),
                Timestamp.from(ReviewSchedule.firstDueAt(at, speed)));
        if (inserted == 0 && atomId != null) {
            jdbc.update("""
                    UPDATE review_pool SET atom_id = ?
                    WHERE topic_id = ? AND exercise_id = ? AND atom_id IS DISTINCT FROM ?
                    """, atomId, topicId, exerciseId, atomId);
        }
        return inserted > 0;
    }

    /** A lesson answer whose exercise is not in the pool yet, with when it was last answered. */
    public record Unenrolled(String topicId, String exerciseId, String atomId, Instant answeredAt) {
    }

    /**
     * Exercises answered in a lesson that have no pool row. Before enrollment
     * moved to answer time, only a fully completed lesson (Boss Fight included)
     * put its exercises in the pool, so a half-finished topic left everything
     * answered so far out of review; this is how those are found. Discovery
     * answers come back too — the caller filters to practice exercises.
     */
    public List<Unenrolled> unenrolledLessonAnswers() {
        List<Unenrolled> out = new ArrayList<>();
        jdbc.query("""
                SELECT a.topic_id, a.exercise_id, a.atom_id, a.created_at
                FROM lesson_exercise_answer a
                WHERE a.context = 'lesson'
                  AND NOT EXISTS (
                      SELECT 1 FROM review_pool p
                      WHERE p.topic_id = a.topic_id AND p.exercise_id = a.exercise_id)
                ORDER BY a.topic_id, a.exercise_id
                """, rs -> {
            out.add(new Unenrolled(rs.getString("topic_id"), rs.getString("exercise_id"),
                    rs.getString("atom_id"), instant(rs.getTimestamp("created_at"))));
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
     * Records a review answer and moves the exercise along the ladder.
     *
     * <p>Read-then-write rather than one UPDATE because the interval table has to
     * live in {@link ReviewSchedule}, where it can be unit-tested, instead of
     * being spelled out in SQL.
     *
     * @param answeredAt when the answer was actually given — an answer queued
     *                   offline is scheduled from then, not from when it finally
     *                   reached the server. Null falls back to now; a value from
     *                   a badly-set clock is clamped rather than dropped.
     * @param answerId   identity of this answer, so a redelivery is a no-op. The
     *                   outbox abandons a request after 8s and keeps it queued
     *                   even when the server did process it; without this, that
     *                   replay would climb the ladder a second time.
     */
    @Transactional
    public void recordAnswer(String topicId, String exerciseId, boolean correct,
                             Instant answeredAt, String answerId) {
        List<Locked> rows = jdbc.query("""
                SELECT id, srs_step, due_at, graduated_at, last_answer_id
                FROM review_pool
                WHERE topic_id = ? AND exercise_id = ?
                FOR UPDATE
                """, (rs, i) -> new Locked(rs.getLong("id"), rs.getInt("srs_step"),
                instant(rs.getTimestamp("due_at")), instant(rs.getTimestamp("graduated_at")),
                rs.getString("last_answer_id")), topicId, exerciseId);

        if (rows.isEmpty()) {
            return;
        }
        Locked row = rows.get(0);
        if (answerId != null && answerId.equals(row.lastAnswerId())) {
            return; // redelivery of an answer already applied
        }
        Instant at = trustedAnswerTime(answeredAt);
        State state = ReviewSchedule.stateOf(at, row.dueAt(), row.srsStep(), row.graduatedAt(), speed);
        Next next = ReviewSchedule.next(at, row.srsStep(), state, correct, speed);

        // A graduated row keeps the due time it had; everything else gets a new one.
        Instant dueAt = next.dueAt() != null ? next.dueAt() : row.dueAt();
        Instant graduatedAt = next.graduated()
                ? (row.graduatedAt() != null ? row.graduatedAt() : at)
                : null;

        jdbc.update("""
                UPDATE review_pool SET
                    last_reviewed_at = ?,
                    last_correct = ?,
                    correct_count = correct_count + CASE WHEN ? THEN 1 ELSE 0 END,
                    wrong_count = wrong_count + CASE WHEN ? THEN 0 ELSE 1 END,
                    lapse_count = lapse_count + CASE WHEN ? THEN 1 ELSE 0 END,
                    srs_step = ?,
                    due_at = ?,
                    graduated_at = ?,
                    last_answer_id = ?
                WHERE id = ?
                """, Timestamp.from(at), correct, correct, correct, next.lapsed(),
                next.step(), Timestamp.from(dueAt), timestamp(graduatedAt), answerId, row.id());
    }

    /**
     * Puts every exercise back on the first rung. Destructive — it discards the
     * repetition schedule the whole feature is built on, so it is reachable only
     * from the settings dialog behind a confirmation. Lifetime counters survive:
     * they are history, not schedule.
     */
    public void restartAll() {
        jdbc.update("""
                UPDATE review_pool SET
                    srs_step = 0,
                    due_at = now(),
                    graduated_at = NULL,
                    lapse_count = 0,
                    last_answer_id = NULL
                """);
    }

    /** The columns {@link #recordAnswer} needs under the row lock. */
    private record Locked(long id, int srsStep, Instant dueAt, Instant graduatedAt, String lastAnswerId) {
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

    /**
     * Clamps a client-supplied answer time into a range worth believing. A phone
     * whose clock runs fast must not push its answers into the future, and one
     * that has been off for months must not backdate them past every window —
     * but neither may cost the user the answer, so this clamps instead of
     * rejecting. Replays are caught by {@code answerId}, not by this.
     */
    private static Instant trustedAnswerTime(Instant answeredAt) {
        Instant now = Instant.now();
        if (answeredAt == null || answeredAt.isAfter(now)) {
            return now;
        }
        Instant floor = now.minus(MAX_BACKDATE);
        return answeredAt.isBefore(floor) ? floor : answeredAt;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp timestamp(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }
}
