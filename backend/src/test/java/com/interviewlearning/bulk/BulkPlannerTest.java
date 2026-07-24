package com.interviewlearning.bulk;

import com.interviewlearning.bulk.BulkPlanner.Decision;
import com.interviewlearning.bulk.BulkPlanner.Proceed;
import com.interviewlearning.bulk.BulkPlanner.Stop;
import com.interviewlearning.bulk.BulkPlanner.WaitUntil;
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

    // --- decide ------------------------------------------------------------

    @Test
    void proceedsWhenUnderCapAndWindowOutlivesEnd() {
        Decision d = BulkPlanner.decide(NOW, END, 50, 20.0, 25, RESET_AFTER_END);
        assertInstanceOf(Proceed.class, d);
    }

    @Test
    void budgetIs100WhileWindowResetsBeforeEnd() {
        // 75% + 20% delta breaks a 50% cap, but the window resets before endTime.
        Decision d = BulkPlanner.decide(NOW, END, 50, 20.0, 75, RESET_BEFORE_END);
        assertInstanceOf(Proceed.class, d);
    }

    @Test
    void waitsForResetWhenOverBudgetAndResetIsBeforeEnd() {
        Decision d = BulkPlanner.decide(NOW, END, 50, 20.0, 95, RESET_BEFORE_END);
        WaitUntil wait = assertInstanceOf(WaitUntil.class, d);
        assertEquals(RESET_BEFORE_END.plusSeconds(BulkPlanner.RESET_SLACK_SECONDS), wait.until());
    }

    @Test
    void stopsAtCapWhenWindowOutlivesEnd() {
        Decision d = BulkPlanner.decide(NOW, END, 50, 20.0, 45, RESET_AFTER_END);
        Stop stop = assertInstanceOf(Stop.class, d);
        assertEquals("capReached", stop.reason());
    }

    @Test
    void stopsWhenEndTimeReached() {
        Decision d = BulkPlanner.decide(END, END, 50, 5.0, 0, RESET_BEFORE_END);
        Stop stop = assertInstanceOf(Stop.class, d);
        assertEquals("endTime", stop.reason());
    }

    @Test
    void stopsWithoutResetInfoWhenOverBudget() {
        Decision d = BulkPlanner.decide(NOW, END, 50, 20.0, 45, null);
        Stop stop = assertInstanceOf(Stop.class, d);
        assertEquals("noResetInfo", stop.reason());
    }

    @Test
    void firstItemUsesAssumedDelta() {
        // 89 + 10 <= 100 -> proceed; 91 + 10 > 100 -> wait for the reset.
        assertInstanceOf(Proceed.class, BulkPlanner.decide(NOW, END, 100, null, 89, RESET_BEFORE_END));
        assertInstanceOf(WaitUntil.class, BulkPlanner.decide(NOW, END, 100, null, 91, RESET_BEFORE_END));
    }

    @Test
    void utilizationAboveHundredIsClamped() {
        // 105% reported + 0 delta must not sneak past a 100% budget check as impossible.
        Decision d = BulkPlanner.decide(NOW, END, 100, 0.0, 105, RESET_BEFORE_END);
        assertInstanceOf(Proceed.class, d);
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
        assertEquals(12.5, BulkPlanner.consumedDelta(30, 42.5, false));
    }

    @Test
    void consumedDeltaAfterMidItemResetIsUtilizationSinceReset() {
        assertEquals(8, BulkPlanner.consumedDelta(90, 8, true));
        // Utilization dropped without a reported reset change: same conservative bound.
        assertEquals(8, BulkPlanner.consumedDelta(90, 8, false));
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
