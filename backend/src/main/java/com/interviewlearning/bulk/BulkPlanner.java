package com.interviewlearning.bulk;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * Pure decision logic for the bulk-generation loop: whether the next item may
 * start given the current Claude 5-hour-window usage, the user's end time and
 * the user's usage cap. No Spring, no clocks — everything is passed in, so the
 * rules are unit-testable.
 *
 * <p>The cap rule: while the current usage window resets before the end time,
 * the budget is effectively 100% (limits refresh before the user cares); once
 * the window outlives the end time, projected usage must stay under the cap.
 */
public final class BulkPlanner {

    /**
     * Assumed usage cost (percent of the 5-hour window) of the first generation,
     * before any real per-item delta has been observed.
     */
    public static final double FIRST_ITEM_ASSUMED_DELTA = 10.0;

    /** Start slightly after the reported reset so the window has really rolled over. */
    public static final long RESET_SLACK_SECONDS = 60;

    private BulkPlanner() {
    }

    public sealed interface Decision permits Proceed, WaitUntil, Stop {
    }

    /** Start the next generation now. */
    public record Proceed() implements Decision {
    }

    /** Pause until the usage window resets, then re-evaluate. */
    public record WaitUntil(Instant until) implements Decision {
    }

    /** End the run: "endTime", "capReached" or "noResetInfo". */
    public record Stop(String reason) implements Decision {
    }

    /**
     * Decides whether the next item may start.
     *
     * @param now              current time
     * @param endTime          when the run must stop
     * @param capPercent       max utilization allowed once the window outlives endTime
     * @param maxObservedDelta biggest single-item usage delta seen so far (null before the first)
     * @param utilization      current 5-hour-window utilization, 0-100
     * @param resetsAt         when the current window resets (null when unknown)
     */
    public static Decision decide(Instant now, Instant endTime, double capPercent,
                                  Double maxObservedDelta, double utilization, Instant resetsAt) {
        if (!now.isBefore(endTime)) {
            return new Stop("endTime");
        }
        double delta = maxObservedDelta != null ? maxObservedDelta : FIRST_ITEM_ASSUMED_DELTA;
        boolean resetsBeforeEnd = resetsAt != null && !resetsAt.isAfter(endTime);
        double budget = resetsBeforeEnd ? 100.0 : capPercent;
        if (Math.min(utilization, 100.0) + delta <= budget) {
            return new Proceed();
        }
        if (resetsAt == null) {
            return new Stop("noResetInfo");
        }
        if (!resetsBeforeEnd) {
            return new Stop("capReached");
        }
        return new WaitUntil(resetsAt.plusSeconds(RESET_SLACK_SECONDS));
    }

    /**
     * Resolves an "HH:mm" time-of-day to an instant: today at that time, or
     * tomorrow when it is not strictly in the future.
     *
     * @throws DateTimeParseException on malformed input
     */
    public static Instant resolveEndTime(String hhmm, ZonedDateTime now) {
        LocalTime time = LocalTime.parse(hhmm.trim());
        ZonedDateTime candidate = now.with(time).withSecond(0).withNano(0);
        return (candidate.isAfter(now) ? candidate : candidate.plusDays(1)).toInstant();
    }

    /**
     * Usage consumed by one generation. When the window reset mid-item (the
     * reset timestamp changed, or utilization dropped), the utilization since
     * the reset is the best conservative lower bound.
     */
    public static double consumedDelta(double utilBefore, double utilAfter, boolean resetChanged) {
        if (!resetChanged && utilAfter >= utilBefore) {
            return utilAfter - utilBefore;
        }
        return utilAfter;
    }

    /** Lenient ISO-8601 parse of the usage endpoint's resets_at; null when absent/bad. */
    public static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(iso).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
