package com.interviewlearning.usage;

/**
 * DTOs for AI usage/status shown in the app header.
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
     * @param providerId   selected provider id
     * @param providerName selected provider label
     * @param installed    whether the provider CLI can be started locally
     * @param downloadUrl  where to install the provider CLI when missing
     * @param available whether session/weekly figures are present and trustworthy
     * @param session   the current 5-hour window (nullable when unavailable)
     * @param weekly    the 7-day window (nullable when unavailable)
     * @param error     a short reason when {@code available} is false (nullable)
     */
    public record UsageSnapshot(String providerId, String providerName, boolean installed, String downloadUrl,
                                boolean available, UsageWindow session, UsageWindow weekly, String error) {

        public static UsageSnapshot unavailable(String error) {
            return unavailable("claude", "Claude", true, "https://claude.com/claude-code", error);
        }

        public static UsageSnapshot unavailable(String providerId, String providerName,
                                                boolean installed, String downloadUrl, String error) {
            return new UsageSnapshot(providerId, providerName, installed, downloadUrl, false, null, null, error);
        }
    }
}
