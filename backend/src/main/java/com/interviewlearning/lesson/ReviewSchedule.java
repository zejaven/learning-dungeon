package com.interviewlearning.lesson;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure spaced-repetition policy for the global review pool. No Spring, no
 * clocks — {@code now} is always a parameter, so every rule is unit-testable
 * (same shape as {@link com.interviewlearning.bulk.BulkPlanner}).
 *
 * <p>An exercise enters the pool when its lesson is completed and then walks a
 * fixed ladder of intervals. It is only offered while it is DUE, and it leaves
 * the pool for good once the whole ladder is walked.
 *
 * <p>Two rules make this different from "answer it correctly and it is gone":
 * <ul>
 *   <li>Being due is a <b>window</b>, not an instant: the exercise stays
 *   answerable for {@link #window(int, double)} after its due time. Without a
 *   window an exercise answered three weeks late would still count as a
 *   properly spaced repetition.
 *   <li>Missing that window is a <b>lapse</b>: the exercise comes back
 *   immediately, and the answer that recovers it only re-earns step 0 — a
 *   correct answer does NOT advance the ladder. That is the one thing a lapse
 *   does that a wrong answer does not, and it is why LAPSED is its own state.
 * </ul>
 *
 * <p>A wrong answer resets to step 0 as well (Leitner), on top of the in-session
 * requeue the review screen already does.
 *
 * <p>The ladder lives here and only here — the frontend is handed
 * {@code step}/{@code dueAt}/{@code expiresAt} already computed, so there is no
 * mirrored copy to keep in sync (unlike {@code LessonUnits}).
 */
public final class ReviewSchedule {

    /**
     * Wait from the last answer to the next one, indexed by the step about to be
     * served. Walking all seven takes about 92 days, which is the point: the
     * last rungs are what move an answer into long-term memory.
     */
    static final Duration[] INTERVALS = {
            Duration.ofHours(1),
            Duration.ofHours(8),
            Duration.ofDays(1),
            Duration.ofDays(3),
            Duration.ofDays(7),
            Duration.ofDays(21),
            Duration.ofDays(60),
    };

    /**
     * How long an exercise stays answerable once due: the interval itself, so a
     * repetition is never more than twice as late as intended, but floored so a
     * short rung survives a night's sleep or a working day, and capped so the
     * long rungs do not blur into each other.
     */
    static final Duration MIN_WINDOW = Duration.ofHours(12);
    static final Duration MAX_WINDOW = Duration.ofDays(14);

    /** Smallest schedulable gap, so a tiny {@code speed} cannot produce "due in 0". */
    private static final Duration MIN_SCALED = Duration.ofMillis(1);

    private ReviewSchedule() {
    }

    /** Number of rungs; a row at this step has graduated. */
    public static int stepCount() {
        return INTERVALS.length;
    }

    /** Wait before the given step is served. */
    public static Duration interval(int step, double speed) {
        return scale(INTERVALS[clampStep(step)], speed);
    }

    /** How long the given step stays answerable after its due time. */
    public static Duration window(int step, double speed) {
        Duration raw = INTERVALS[clampStep(step)];
        if (raw.compareTo(MIN_WINDOW) < 0) {
            raw = MIN_WINDOW;
        } else if (raw.compareTo(MAX_WINDOW) > 0) {
            raw = MAX_WINDOW;
        }
        return scale(raw, speed);
    }

    /** When the exercise stops being answerable and lapses instead. */
    public static Instant expiresAt(Instant dueAt, int step, double speed) {
        return dueAt.plus(window(step, speed));
    }

    /** When a freshly pooled exercise first comes up. */
    public static Instant firstDueAt(Instant now, double speed) {
        return now.plus(interval(0, speed));
    }

    /** Where a pool row stands right now. */
    public enum State {
        /** Not yet due. */
        WAITING,
        /** Due, and still inside its answering window. */
        DUE,
        /** The window passed unanswered — offered again, but the ladder restarts. */
        LAPSED,
        /** The whole ladder was walked; out of the pool. */
        GRADUATED,
    }

    /**
     * @param dueAt       when the row became/becomes due (never null for a live row)
     * @param graduatedAt when the row left the pool, or null
     */
    public static State stateOf(Instant now, Instant dueAt, int step, Instant graduatedAt, double speed) {
        if (graduatedAt != null) {
            return State.GRADUATED;
        }
        if (now.isBefore(dueAt)) {
            return State.WAITING;
        }
        return now.isAfter(expiresAt(dueAt, step, speed)) ? State.LAPSED : State.DUE;
    }

    /**
     * What to write after an answer.
     *
     * @param dueAt     the new due time, or null when {@code graduated} — the
     *                  caller must then leave the stored due time alone
     * @param graduated the row leaves the pool (or was already out of it)
     * @param lapsed    this answer recovered a missed window, so it re-earned
     *                  step 0 rather than advancing
     */
    public record Next(int step, Instant dueAt, boolean graduated, boolean lapsed) {
    }

    /**
     * Applies an answer given at {@code now}. Pass the state derived for that
     * same instant — an answer queued offline is scheduled from when it was
     * actually given, not from when it reached the server.
     */
    public static Next next(Instant now, int step, State state, boolean correct, double speed) {
        if (state == State.GRADUATED) {
            // Nothing left to schedule (a stale queued answer can land here).
            return new Next(stepCount(), null, true, false);
        }
        boolean lapsed = state == State.LAPSED;
        int nextStep = (lapsed || !correct) ? 0 : step + 1;
        if (nextStep >= INTERVALS.length) {
            return new Next(stepCount(), null, true, false);
        }
        return new Next(nextStep, now.plus(interval(nextStep, speed)), false, lapsed);
    }

    private static int clampStep(int step) {
        if (step < 0) {
            return 0;
        }
        return Math.min(step, INTERVALS.length - 1);
    }

    /**
     * {@code app.review.speed} compresses the whole timeline uniformly so the
     * full ladder — lapses included — can be walked by hand in a minute. It is
     * applied after the window clamp: the clamp sets the shape, the speed only
     * scales it.
     */
    private static Duration scale(Duration d, double speed) {
        if (speed <= 0 || speed == 1.0) {
            return d;
        }
        Duration scaled = Duration.ofNanos(Math.round(d.toNanos() * speed));
        return scaled.compareTo(MIN_SCALED) < 0 ? MIN_SCALED : scaled;
    }
}
