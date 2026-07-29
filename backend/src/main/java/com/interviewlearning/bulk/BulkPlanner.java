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
 * <p>Two modes, distinguished by whether an end time was given:
 * <ul>
 *   <li><b>Deadline</b> (end time set — the overnight case): while the current
 *   usage window resets before the end time, the budget is effectively 100%
 *   (limits refresh before the user cares); once the window outlives the end
 *   time, projected usage must stay under the cap, and exceeding it ends the run.
 *   <li><b>Continuous</b> (no end time — the daytime case): the cap always
 *   applies, so at most {@code cap}% of every 5-hour window is spent and the
 *   rest stays available for the user's own parallel work. Reaching the cap
 *   pauses the run until the window resets instead of ending it.
 * </ul>
 *
 * <p>The 7-day window is guarded the same way but by its own cap, and it can
 * only end a run ({@code weeklyCapReached}), never pause it: its reset is days
 * away, so idling there is neither what an overnight run promised nor what a
 * daytime run means.
 */
public final class BulkPlanner {

    /**
     * Assumed usage cost (percent of the 5-hour window) of the first generation,
     * before any real per-item delta has been observed.
     */
    public static final double FIRST_ITEM_ASSUMED_DELTA = 10.0;

    /**
     * The same for the 7-day window. One generation is a far smaller slice of a
     * week's quota than of a 5-hour one, so the placeholder is smaller; the
     * first real measurement replaces it.
     */
    public static final double FIRST_ITEM_ASSUMED_WEEKLY_DELTA = 3.0;

    /** Start slightly after the reported reset so the window has really rolled over. */
    public static final long RESET_SLACK_SECONDS = 60;

    /**
     * Continuous mode only: how long to wait before re-reading usage when the
     * reading carried no reset timestamp (a deadline run stops instead, but an
     * unattended endless run should survive a transient gap).
     */
    public static final long NO_RESET_RETRY_SECONDS = 600;

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

    /** End the run: "endTime", "capReached", "weeklyCapReached" or "noResetInfo". */
    public record Stop(String reason) implements Decision {
    }

    /**
     * One usage window as the planner sees it. Each window carries its own cap:
     * half of a 5-hour window and half of a week's quota are very different
     * decisions, so the user sets them separately.
     *
     * @param utilization percent consumed, 0-100
     * @param resetsAt    when the window rolls over (null when unknown)
     * @param maxDelta    biggest single-item consumption observed in this window
     *                    (null before the first measurement)
     * @param capPercent  max utilization allowed for this window (once it
     *                    outlives endTime, or always in continuous mode)
     */
    public record Window(double utilization, Instant resetsAt, Double maxDelta, double capPercent) {
    }

    /**
     * Decides whether the next item may start.
     *
     * @param now     current time
     * @param endTime when the run must stop; null = continuous mode
     * @param session the 5-hour window
     * @param weekly  the 7-day window; null when the reading carried none
     */
    public static Decision decide(Instant now, Instant endTime, Window session, Window weekly) {
        if (endTime != null && !now.isBefore(endTime)) {
            return new Stop("endTime");
        }
        // The weekly budget can only veto: its reset is days out, so pausing for
        // it is pointless — the run ends and the user decides what to do next.
        if (weekly != null && exceedsBudget(endTime, weekly, FIRST_ITEM_ASSUMED_WEEKLY_DELTA)) {
            return new Stop("weeklyCapReached");
        }
        return decideSession(now, endTime, session);
    }

    /** True when starting one more item would push this window past its budget. */
    private static boolean exceedsBudget(Instant endTime, Window window, double assumedDelta) {
        double delta = window.maxDelta() != null ? window.maxDelta() : assumedDelta;
        // Only a deadline run may spend freely, and only while the window still
        // resets before that deadline. A continuous run always honours the cap.
        boolean resetsBeforeEnd = endTime != null && window.resetsAt() != null
                && !window.resetsAt().isAfter(endTime);
        double budget = resetsBeforeEnd ? 100.0 : window.capPercent();
        return Math.min(window.utilization(), 100.0) + delta > budget;
    }

    /** The 5-hour window may pause the run: its reset is hours away at most. */
    private static Decision decideSession(Instant now, Instant endTime, Window session) {
        Instant resetsAt = session.resetsAt();
        boolean resetsBeforeEnd = endTime != null && resetsAt != null && !resetsAt.isAfter(endTime);
        if (!exceedsBudget(endTime, session, FIRST_ITEM_ASSUMED_DELTA)) {
            return new Proceed();
        }
        if (resetsAt == null) {
            // No idea when the budget frees up: a deadline run gives up, an
            // endless one re-checks later.
            return endTime != null
                    ? new Stop("noResetInfo")
                    : new WaitUntil(now.plusSeconds(NO_RESET_RETRY_SECONDS));
        }
        if (endTime != null && !resetsBeforeEnd) {
            // Waiting for the reset would overshoot the deadline anyway.
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
     * Usage consumed by one generation.
     *
     * <p>A window rollover is detected by utilization <em>dropping</em>, never by
     * the reported reset timestamp changing: the endpoint derives {@code resets_at}
     * from the request time, so it jitters on every single fetch. Treating that
     * jitter as a rollover would record the whole window's utilization as one
     * item's cost and stall the run far below its cap.
     *
     * <p>After a real rollover only the tail of the item lives in the fresh
     * window, so its utilization is the best available lower bound.
     */
    public static double consumedDelta(double utilBefore, double utilAfter) {
        return utilAfter >= utilBefore ? utilAfter - utilBefore : utilAfter;
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
