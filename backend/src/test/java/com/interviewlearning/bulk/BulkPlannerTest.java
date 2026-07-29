package com.interviewlearning.bulk;

import com.interviewlearning.bulk.BulkPlanner.Decision;
import com.interviewlearning.bulk.BulkPlanner.Proceed;
import com.interviewlearning.bulk.BulkPlanner.Stop;
import com.interviewlearning.bulk.BulkPlanner.WaitUntil;
import com.interviewlearning.bulk.BulkPlanner.Window;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BulkPlannerTest {

    private static final Instant NOW = Instant.parse("2026-07-25T20:00:00Z");
    private static final Instant END = Instant.parse("2026-07-26T07:00:00Z");
    private static final Instant RESET_BEFORE_END = Instant.parse("2026-07-25T23:30:00Z");
    private static final Instant RESET_AFTER_END = Instant.parse("2026-07-26T08:30:00Z");
    private static final Instant WEEKLY_RESET = Instant.parse("2026-07-29T09:00:00Z");

    private static Window window(double utilization, Instant resetsAt, Double maxDelta, double cap) {
        return new Window(utilization, resetsAt, maxDelta, cap);
    }

    /** Session-only decision (weekly reading absent). */
    private static Decision decide(Instant now, Instant endTime, double cap,
                                   Double maxDelta, double utilization, Instant resetsAt) {
        return BulkPlanner.decide(now, endTime, window(utilization, resetsAt, maxDelta, cap), null);
    }

    // --- decide: 5-hour window, deadline mode -------------------------------

    @Test
    void proceedsWhenUnderCapAndWindowOutlivesEnd() {
        assertInstanceOf(Proceed.class, decide(NOW, END, 50, 20.0, 25, RESET_AFTER_END));
    }

    @Test
    void budgetIs100WhileWindowResetsBeforeEnd() {
        // 75% + 20% delta breaks a 50% cap, but the window resets before endTime.
        assertInstanceOf(Proceed.class, decide(NOW, END, 50, 20.0, 75, RESET_BEFORE_END));
    }

    @Test
    void waitsForResetWhenOverBudgetAndResetIsBeforeEnd() {
        Decision d = decide(NOW, END, 50, 20.0, 95, RESET_BEFORE_END);
        WaitUntil wait = assertInstanceOf(WaitUntil.class, d);
        assertEquals(RESET_BEFORE_END.plusSeconds(BulkPlanner.RESET_SLACK_SECONDS), wait.until());
    }

    @Test
    void stopsAtCapWhenWindowOutlivesEnd() {
        Stop stop = assertInstanceOf(Stop.class, decide(NOW, END, 50, 20.0, 45, RESET_AFTER_END));
        assertEquals("capReached", stop.reason());
    }

    @Test
    void stopsWhenEndTimeReached() {
        Stop stop = assertInstanceOf(Stop.class, decide(END, END, 50, 5.0, 0, RESET_BEFORE_END));
        assertEquals("endTime", stop.reason());
    }

    @Test
    void stopsWithoutResetInfoWhenOverBudget() {
        Stop stop = assertInstanceOf(Stop.class, decide(NOW, END, 50, 20.0, 45, null));
        assertEquals("noResetInfo", stop.reason());
    }

    @Test
    void firstItemUsesAssumedDelta() {
        // 89 + 10 <= 100 -> proceed; 91 + 10 > 100 -> wait for the reset.
        assertInstanceOf(Proceed.class, decide(NOW, END, 100, null, 89, RESET_BEFORE_END));
        assertInstanceOf(WaitUntil.class, decide(NOW, END, 100, null, 91, RESET_BEFORE_END));
    }

    @Test
    void utilizationAboveHundredIsClamped() {
        // 105% reported + 0 delta must not sneak past a 100% budget check as impossible.
        assertInstanceOf(Proceed.class, decide(NOW, END, 100, 0.0, 105, RESET_BEFORE_END));
    }

    // --- decide: continuous mode (no end time) ------------------------------

    @Test
    void continuousModeProceedsUnderCap() {
        assertInstanceOf(Proceed.class, decide(NOW, null, 50, 20.0, 25, RESET_BEFORE_END));
    }

    @Test
    void continuousModeHonoursCapEvenWhenTheWindowResetsSoon() {
        // The deadline mode would spend freely here; an endless run must not.
        Decision d = decide(NOW, null, 50, 20.0, 45, RESET_BEFORE_END);
        WaitUntil wait = assertInstanceOf(WaitUntil.class, d);
        assertEquals(RESET_BEFORE_END.plusSeconds(BulkPlanner.RESET_SLACK_SECONDS), wait.until());
    }

    @Test
    void continuousModeNeverStopsAtTheCap() {
        // Whatever the reset time, an endless run pauses and resumes later.
        Decision d = decide(NOW, null, 50, 20.0, 95, RESET_AFTER_END);
        WaitUntil wait = assertInstanceOf(WaitUntil.class, d);
        assertEquals(RESET_AFTER_END.plusSeconds(BulkPlanner.RESET_SLACK_SECONDS), wait.until());
    }

    @Test
    void continuousModeRetriesWhenResetInfoIsMissing() {
        Decision d = decide(NOW, null, 50, 20.0, 45, null);
        WaitUntil wait = assertInstanceOf(WaitUntil.class, d);
        assertEquals(NOW.plusSeconds(BulkPlanner.NO_RESET_RETRY_SECONDS), wait.until());
    }

    // --- decide: 7-day window ------------------------------------------------

    @Test
    void weeklyWindowUnderCapDoesNotInterfere() {
        Decision d = BulkPlanner.decide(NOW, END,
                window(10, RESET_BEFORE_END, 5.0, 80), window(40, WEEKLY_RESET, 2.0, 90));
        assertInstanceOf(Proceed.class, d);
    }

    @Test
    void weeklyCapStopsTheRunEvenWhenTheSessionWindowIsFine() {
        Decision d = BulkPlanner.decide(NOW, END,
                window(5, RESET_BEFORE_END, 5.0, 80), window(89, WEEKLY_RESET, 2.0, 90));
        Stop stop = assertInstanceOf(Stop.class, d);
        assertEquals("weeklyCapReached", stop.reason());
    }

    @Test
    void eachWindowHonoursItsOwnCap() {
        // Session at 60% passes its 80% cap; weekly at 60% breaks its 50% cap.
        assertInstanceOf(Proceed.class, BulkPlanner.decide(NOW, END,
                window(60, RESET_AFTER_END, 5.0, 80), window(60, WEEKLY_RESET, 2.0, 90)));
        Stop stop = assertInstanceOf(Stop.class, BulkPlanner.decide(NOW, END,
                window(60, RESET_AFTER_END, 5.0, 80), window(60, WEEKLY_RESET, 2.0, 50)));
        assertEquals("weeklyCapReached", stop.reason());
    }

    @Test
    void weeklyCapStopsAContinuousRunToo() {
        // The session window would only pause; the weekly one ends the run.
        Decision d = BulkPlanner.decide(NOW, null,
                window(95, RESET_BEFORE_END, 5.0, 80), window(89, WEEKLY_RESET, 2.0, 90));
        Stop stop = assertInstanceOf(Stop.class, d);
        assertEquals("weeklyCapReached", stop.reason());
    }

    @Test
    void weeklyBudgetIs100WhenItResetsBeforeTheDeadline() {
        Instant weeklyResetsSoon = Instant.parse("2026-07-26T06:00:00Z");
        Decision d = BulkPlanner.decide(NOW, END,
                window(5, RESET_BEFORE_END, 5.0, 50), window(80, weeklyResetsSoon, 2.0, 50));
        assertInstanceOf(Proceed.class, d);
    }

    @Test
    void weeklyFirstItemUsesItsOwnSmallerAssumedDelta() {
        // 96 + 3 <= 100 -> allowed; 98 + 3 > 100 -> stopped.
        assertInstanceOf(Proceed.class, BulkPlanner.decide(NOW, END,
                window(0, RESET_BEFORE_END, 0.0, 100), window(96, WEEKLY_RESET, null, 100)));
        Stop stop = assertInstanceOf(Stop.class, BulkPlanner.decide(NOW, END,
                window(0, RESET_BEFORE_END, 0.0, 100), window(98, WEEKLY_RESET, null, 100)));
        assertEquals("weeklyCapReached", stop.reason());
    }

    @Test
    void endTimeWinsOverTheWeeklyCap() {
        Decision d = BulkPlanner.decide(END, END,
                window(0, RESET_BEFORE_END, 1.0, 50), window(99, WEEKLY_RESET, 2.0, 50));
        Stop stop = assertInstanceOf(Stop.class, d);
        assertEquals("endTime", stop.reason());
    }

    // --- resolveEndTime ----------------------------------------------------

    @Test
    void endTimeInFutureResolvesToday() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 25, 21, 0, 0, 0, ZoneId.of("UTC"));
        assertEquals(Instant.parse("2026-07-25T23:30:00Z"), BulkPlanner.resolveEndTime("23:30", now));
    }

    @Test
    void endTimeInPastResolvesTomorrow() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 25, 21, 0, 0, 0, ZoneId.of("UTC"));
        assertEquals(Instant.parse("2026-07-26T10:00:00Z"), BulkPlanner.resolveEndTime("10:00", now));
    }

    @Test
    void endTimeExactlyNowResolvesTomorrow() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 25, 21, 0, 0, 0, ZoneId.of("UTC"));
        assertEquals(Instant.parse("2026-07-26T21:00:00Z"), BulkPlanner.resolveEndTime("21:00", now));
    }

    @Test
    void malformedEndTimeThrows() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 25, 21, 0, 0, 0, ZoneId.of("UTC"));
        assertThrows(DateTimeParseException.class, () -> BulkPlanner.resolveEndTime("25:99", now));
    }

    // --- consumedDelta ------------------------------------------------------

    @Test
    void consumedDeltaIsSimpleDifference() {
        assertEquals(12.5, BulkPlanner.consumedDelta(30, 42.5));
    }

    @Test
    void consumedDeltaAfterMidItemResetIsUtilizationSinceReset() {
        // A dropping reading is the only reliable sign that the window rolled over.
        assertEquals(8, BulkPlanner.consumedDelta(90, 8));
    }

    @Test
    void consumedDeltaIgnoresJitteringResetTimestamps() {
        // The endpoint re-derives resets_at on every fetch; that must not be read
        // as a rollover, or one item would be charged the whole window's usage.
        assertEquals(5, BulkPlanner.consumedDelta(21, 26));
    }

    // --- parseInstant -------------------------------------------------------

    @Test
    void parsesIsoInstantAndOffsetForms() {
        assertEquals(Instant.parse("2026-07-25T23:30:00Z"),
                BulkPlanner.parseInstant("2026-07-25T23:30:00Z"));
        assertEquals(Instant.parse("2026-07-25T20:30:00Z"),
                BulkPlanner.parseInstant("2026-07-25T23:30:00+03:00"));
        assertNull(BulkPlanner.parseInstant(null));
        assertNull(BulkPlanner.parseInstant("not-a-date"));
    }
}
