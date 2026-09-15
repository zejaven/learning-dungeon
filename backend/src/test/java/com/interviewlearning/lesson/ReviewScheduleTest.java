package com.interviewlearning.lesson;

import com.interviewlearning.lesson.ReviewSchedule.Next;
import com.interviewlearning.lesson.ReviewSchedule.State;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the spaced-repetition policy. These rules decide when every pooled
 * exercise is shown for months, and the migration that feeds them cannot be
 * tested (the project has no database tests), so this is the whole safety net —
 * change an assertion only when the policy itself is meant to change.
 */
class ReviewScheduleTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final double FULL = 1.0;

    /** The ladder as documented, so a reordering of INTERVALS fails loudly here. */
    private static final Duration[] EXPECTED_INTERVALS = {
            Duration.ofHours(1), Duration.ofHours(8), Duration.ofDays(1),
            Duration.ofDays(3), Duration.ofDays(7), Duration.ofDays(21), Duration.ofDays(60),
    };

    private static State stateAt(Instant now, Instant dueAt, int step) {
        return ReviewSchedule.stateOf(now, dueAt, step, null, FULL);
    }

    private static Next answer(Instant now, int step, State state, boolean correct) {
        return ReviewSchedule.next(now, step, state, correct, FULL);
    }

    // --- the ladder itself ---------------------------------------------------

    @Test
    void ladderIsSevenStepsInTheDocumentedOrder() {
        assertEquals(7, ReviewSchedule.stepCount());
        for (int step = 0; step < EXPECTED_INTERVALS.length; step++) {
            assertEquals(EXPECTED_INTERVALS[step], ReviewSchedule.interval(step, FULL),
                    "interval at step " + step);
        }
    }

    @Test
    void aFreshlyPooledExerciseIsNotDueImmediately() {
        assertEquals(NOW.plus(Duration.ofHours(1)), ReviewSchedule.firstDueAt(NOW, FULL));
    }

    @Test
    void windowIsTheIntervalFlooredAtTwelveHoursAndCappedAtTwoWeeks() {
        assertEquals(Duration.ofHours(12), ReviewSchedule.window(0, FULL)); // 1h  -> floor
        assertEquals(Duration.ofHours(12), ReviewSchedule.window(1, FULL)); // 8h  -> floor
        assertEquals(Duration.ofDays(1), ReviewSchedule.window(2, FULL));   // 1d  -> itself
        assertEquals(Duration.ofDays(3), ReviewSchedule.window(3, FULL));   // 3d  -> itself
        assertEquals(Duration.ofDays(7), ReviewSchedule.window(4, FULL));   // 7d  -> itself
        assertEquals(Duration.ofDays(14), ReviewSchedule.window(5, FULL));  // 21d -> cap
        assertEquals(Duration.ofDays(14), ReviewSchedule.window(6, FULL));  // 60d -> cap
    }

    // --- state derivation ----------------------------------------------------

    @Test
    void waitsUntilTheInstantItFallsDue() {
        Instant due = NOW.plus(Duration.ofHours(1));
        assertEquals(State.WAITING, stateAt(due.minusNanos(1), due, 0));
        assertEquals(State.DUE, stateAt(due, due, 0));
    }

    @Test
    void staysDueThroughTheLastInstantOfItsWindow() {
        Instant due = NOW;
        Instant expires = ReviewSchedule.expiresAt(due, 2, FULL); // step 2 -> 24h window
        assertEquals(due.plus(Duration.ofDays(1)), expires);
        assertEquals(State.DUE, stateAt(expires, due, 2));
        assertEquals(State.LAPSED, stateAt(expires.plusNanos(1), due, 2));
    }

    @Test
    void graduatedOutranksEveryTimeComparison() {
        Instant due = NOW.minus(Duration.ofDays(365));
        assertEquals(State.GRADUATED, ReviewSchedule.stateOf(NOW, due, 6, NOW, FULL));
    }

    // --- answering -----------------------------------------------------------

    @Test
    void aCorrectAnswerInsideTheWindowClimbsOneRung() {
        for (int step = 0; step < ReviewSchedule.stepCount() - 1; step++) {
            Next next = answer(NOW, step, State.DUE, true);
            assertEquals(step + 1, next.step(), "from step " + step);
            assertFalse(next.graduated(), "from step " + step);
            assertFalse(next.lapsed(), "from step " + step);
            assertEquals(NOW.plus(EXPECTED_INTERVALS[step + 1]), next.dueAt(), "from step " + step);
        }
    }

    @Test
    void theLastRungGraduatesAndLeavesTheScheduleAlone() {
        Next next = answer(NOW, ReviewSchedule.stepCount() - 1, State.DUE, true);
        assertTrue(next.graduated());
        assertEquals(ReviewSchedule.stepCount(), next.step());
        assertNull(next.dueAt(), "a graduated row keeps whatever due time it had");
    }

    @Test
    void aWrongAnswerFallsAllTheWayBackFromEveryRung() {
        for (int step = 0; step < ReviewSchedule.stepCount(); step++) {
            Next next = answer(NOW, step, State.DUE, false);
            assertEquals(0, next.step(), "from step " + step);
            assertFalse(next.graduated(), "from step " + step);
            assertEquals(NOW.plus(EXPECTED_INTERVALS[0]), next.dueAt(), "from step " + step);
        }
    }

    @Test
    void recoveringAMissedWindowReEarnsStepZeroEvenWhenAnsweredCorrectly() {
        Next next = answer(NOW, 5, State.LAPSED, true);
        assertEquals(0, next.step());
        assertTrue(next.lapsed());
        assertFalse(next.graduated());
        assertEquals(NOW.plus(EXPECTED_INTERVALS[0]), next.dueAt());
    }

    @Test
    void aMissedLastRungCannotGraduate() {
        Next next = answer(NOW, ReviewSchedule.stepCount() - 1, State.LAPSED, true);
        assertFalse(next.graduated());
        assertEquals(0, next.step());
    }

    @Test
    void answeringAGraduatedRowChangesNothing() {
        Next next = answer(NOW, ReviewSchedule.stepCount(), State.GRADUATED, false);
        assertTrue(next.graduated());
        assertNull(next.dueAt());
    }

    // --- walking the whole ladder --------------------------------------------

    @Test
    void answeringOnTimeEveryTimeGraduatesAfterSevenAnswers() {
        Instant now = NOW;
        Instant due = ReviewSchedule.firstDueAt(now, FULL);
        int step = 0;
        int answers = 0;
        while (true) {
            now = due; // answer the moment it falls due
            assertEquals(State.DUE, stateAt(now, due, step));
            Next next = answer(now, step, State.DUE, true);
            answers++;
            if (next.graduated()) {
                break;
            }
            step = next.step();
            due = next.dueAt();
        }
        assertEquals(7, answers);
        // 1h + 8h + 1d + 3d + 7d + 21d + 60d
        assertEquals(Duration.ofDays(92).plusHours(9), Duration.between(NOW, now));
    }

    @Test
    void sleepingThroughOneWindowCostsTheWholeLadder() {
        // Due at step 5 (three weeks in), answered a fortnight and a second late.
        Instant due = NOW;
        Instant late = ReviewSchedule.expiresAt(due, 5, FULL).plusSeconds(1);
        assertEquals(State.LAPSED, stateAt(late, due, 5));
        assertEquals(0, answer(late, 5, State.LAPSED, true).step());
    }

    // --- the speed knob ------------------------------------------------------

    @Test
    void speedCompressesIntervalsAndWindowsAlike() {
        double speed = 0.0005;
        assertEquals(Duration.ofNanos(Math.round(Duration.ofHours(1).toNanos() * speed)),
                ReviewSchedule.interval(0, speed));
        assertEquals(Duration.ofNanos(Math.round(Duration.ofHours(12).toNanos() * speed)),
                ReviewSchedule.window(0, speed), "a window that did not shrink could never be missed");
    }

    @Test
    void anAbsurdSpeedStillProducesAForwardMovingSchedule() {
        Next next = ReviewSchedule.next(NOW, 0, State.DUE, true, 1e-12);
        assertTrue(next.dueAt().isAfter(NOW));
    }

    @Test
    void aNonPositiveSpeedIsIgnoredRatherThanFreezingTheSchedule() {
        assertEquals(Duration.ofHours(1), ReviewSchedule.interval(0, 0));
        assertEquals(Duration.ofHours(1), ReviewSchedule.interval(0, -3));
    }
}
