package com.interviewlearning.bulk;

import java.util.List;

/** DTOs for the bulk-generation loop (start request + status polling). */
public final class BulkDtos {

    private BulkDtos() {
    }

    /**
     * {@code POST /api/bulk/start} body. The item list is built by the frontend
     * because the java question catalog lives there; the backend re-checks each
     * item at execution time and skips ones that are no longer missing.
     */
    public record StartRequest(String kind,       // "theory" | "atoms"
                               String provider,   // must be "claude"
                               String domainId,   // informational, shown in the strip
                               String endTime,    // "HH:mm" local time-of-day
                               Integer maxPercent, // 0..100
                               List<BulkItem> items) {
    }

    /**
     * One unit of work. Theory items carry the same fields the single-item
     * {@code POST /api/topics/generate} takes; atoms items only need the topic id.
     */
    public record BulkItem(String id,             // catalog entry id (theory) | topic id (atoms)
                           LocalizedLabel label,
                           String question, String catalogId, String categoryId,
                           Integer difficulty, String style, String styleName) {
    }

    public record LocalizedLabel(String en, String ru) {
    }

    // --- status -------------------------------------------------------------

    public record StatusResponse(boolean active, RunView run) {
    }

    public record RunView(String kind, String domainId, String provider,
                          String endTime,          // resolved ISO instant
                          int maxPercent,
                          List<ItemView> items,
                          int currentIndex,        // -1 before the first item starts
                          String phase,            // generating|waitingPace|waitingUsage|waitingReset
                                                   // |finished|stopped|endReached|capReached|noResetInfo
                          String waitUntil,        // ISO instant while waiting, else null
                          boolean stopRequested,
                          String startedAt,
                          String finishedAt,       // null while active
                          Double utilization,      // last 5-hour-window reading, null before the first
                          String resetsAt,
                          Double maxDeltaObserved) {
    }

    public record ItemView(String id, LocalizedLabel label,
                           String status,          // pending|running|done|error|skipped
                           String taskKey) {
    }
}
