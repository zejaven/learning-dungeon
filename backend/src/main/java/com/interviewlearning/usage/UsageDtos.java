package com.interviewlearning.usage;

/**
 * DTOs for the Claude subscription usage shown in the app header. Mirrors the
 * shape returned by Anthropic's OAuth usage endpoint, trimmed to what the UI
 * needs (the current 5-hour session window and the 7-day weekly window).
 */
public final class UsageDtos {

    private UsageDtos() {
    }

    /**
     * One usage window.
     *
     * @param utilization percentage consumed, 0-100
     * @param resetsAt    ISO-8601 UTC timestamp when the window resets (nullable)
     */
    public record UsageWindow(double utilization, String resetsAt) {
    }

    /**
     * A usage reading served to the frontend.
     *
     * @param available whether session/weekly figures are present and trustworthy
     * @param session   the current 5-hour window (nullable when unavailable)
     * @param weekly    the 7-day window (nullable when unavailable)
     * @param error     a short reason when {@code available} is false (nullable)
     */
    public record UsageSnapshot(boolean available, UsageWindow session, UsageWindow weekly, String error) {

        public static UsageSnapshot unavailable(String error) {
            return new UsageSnapshot(false, null, null, error);
        }
    }
}
