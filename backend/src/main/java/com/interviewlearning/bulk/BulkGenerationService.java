package com.interviewlearning.bulk;

import com.interviewlearning.ai.AiTask;
import com.interviewlearning.bulk.BulkDtos.BulkItem;
import com.interviewlearning.bulk.BulkDtos.ItemView;
import com.interviewlearning.bulk.BulkDtos.RunView;
import com.interviewlearning.bulk.BulkDtos.StartRequest;
import com.interviewlearning.bulk.BulkDtos.StatusResponse;
import com.interviewlearning.bulk.BulkPlanner.Decision;
import com.interviewlearning.bulk.BulkPlanner.Proceed;
import com.interviewlearning.bulk.BulkPlanner.Stop;
import com.interviewlearning.bulk.BulkPlanner.WaitUntil;
import com.interviewlearning.bulk.BulkPlanner.Window;
import com.interviewlearning.generation.AtomsPromptBuilder;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.generation.TopicPromptBuilder;
import com.interviewlearning.generation.TopicPromptBuilder.TopicGenSpec;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import com.interviewlearning.usage.UsageDtos.UsageSnapshot;
import com.interviewlearning.usage.UsageService;
import com.interviewlearning.usage.UsageService.BulkRefresh;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs one sequential bulk-generation loop (theory topics or learning-atoms
 * lessons) over the items the frontend sent, gated by Claude 5-hour-window
 * usage between items. One run at a time; state is in-memory only (a backend
 * restart loses it, same as {@link GenerationService} tasks).
 *
 * <p>Each item is started through the regular {@link GenerationService} keys
 * ({@code catalog:<id>} / {@code atoms:<id>}), so per-item SSE logs, the
 * active-task list and key dedupe against manual clicks all keep working.
 *
 * <p>While a run is active the passive usage path is paused
 * ({@link UsageService#setPassivePaused}); only this loop triggers real
 * upstream fetches, spaced by {@link UsageService#nextAllowedFetchAt}.
 */
@Service
public class BulkGenerationService {

    private static final Logger log = LoggerFactory.getLogger(BulkGenerationService.class);

    /** A detached generation has no process timeout; this is the loop's own cap per item. */
    private static final long ITEM_TIMEOUT_MILLIS = 45 * 60_000L;
    /** Sleep granularity while waiting, so Stop stays responsive. */
    private static final long WAIT_CHUNK_MILLIS = 5_000L;
    /** Retry interval when the usage endpoint is unavailable or rate-limited. */
    private static final long USAGE_RETRY_MILLIS = 60_000L;

    private final GenerationService generation;
    private final UsageService usage;
    private final TopicRepository topics;
    private final TopicPromptBuilder topicPrompts;
    private final AtomsPromptBuilder atomsPrompts;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bulk-gen");
        t.setDaemon(true);
        return t;
    });

    private volatile BulkRun current;

    public BulkGenerationService(GenerationService generation,
                                 UsageService usage,
                                 TopicRepository topics,
                                 TopicPromptBuilder topicPrompts,
                                 AtomsPromptBuilder atomsPrompts) {
        this.generation = generation;
        this.usage = usage;
        this.topics = topics;
        this.topicPrompts = topicPrompts;
        this.atomsPrompts = atomsPrompts;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // --- run state ----------------------------------------------------------

    private static final class Item {
        final BulkItem spec;
        volatile String status = "pending"; // pending|running|done|error|skipped
        volatile String taskKey;

        Item(BulkItem spec) {
            this.spec = spec;
        }
    }

    private static final class BulkRun {
        final String kind;      // "theory" | "atoms"
        final String provider;
        final String domainId;
        /** Null in continuous mode (no deadline). */
        final Instant endTime;
        final double capPercent;
        final double weeklyCapPercent;
        final List<Item> items;
        /** Content languages to generate; null/empty = both. */
        final List<String> languages;
        final Instant startedAt = Instant.now();

        volatile int currentIndex = -1;
        volatile String phase = "waitingPace";
        volatile Instant waitUntil;
        volatile boolean stopRequested;
        volatile Instant finishedAt;
        volatile Double maxDelta;
        volatile Double utilization;
        volatile Instant resetsAt;
        volatile Double maxWeeklyDelta;
        volatile Double weeklyUtilization;
        volatile Instant weeklyResetsAt;

        BulkRun(String kind, String provider, String domainId, Instant endTime,
                double capPercent, double weeklyCapPercent, List<Item> items,
                List<String> languages) {
            this.kind = kind;
            this.provider = provider;
            this.domainId = domainId;
            this.endTime = endTime;
            this.capPercent = capPercent;
            this.weeklyCapPercent = weeklyCapPercent;
            this.items = items;
            this.languages = languages;
        }

        boolean isFinished() {
            return finishedAt != null;
        }
    }

    // --- public API ---------------------------------------------------------

    /** @throws IllegalStateException when a run is active; IllegalArgumentException on bad input */
    public synchronized StatusResponse start(StartRequest request) {
        if (current != null && !current.isFinished()) {
            throw new IllegalStateException("A bulk generation run is already active.");
        }
        String kind = request.kind() == null ? "" : request.kind().trim().toLowerCase();
        if (!"theory".equals(kind) && !"atoms".equals(kind)) {
            throw new IllegalArgumentException("kind must be \"theory\" or \"atoms\".");
        }
        String provider = request.provider() == null ? "" : request.provider().trim().toLowerCase();
        if (!"claude".equals(provider)) {
            throw new IllegalArgumentException("Bulk generation is only available for Claude "
                    + "(usage limits of other providers cannot be tracked).");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty.");
        }
        int maxPercent = request.maxPercent() == null ? 100 : request.maxPercent();
        if (maxPercent < 0 || maxPercent > 100) {
            throw new IllegalArgumentException("maxPercent must be between 0 and 100.");
        }
        int maxWeeklyPercent = request.maxWeeklyPercent() == null ? 100 : request.maxWeeklyPercent();
        if (maxWeeklyPercent < 0 || maxWeeklyPercent > 100) {
            throw new IllegalArgumentException("maxWeeklyPercent must be between 0 and 100.");
        }
        // A blank end time means continuous mode: run until the items run out or
        // the user stops, keeping every 5-hour window under the cap.
        Instant endTime = null;
        if (request.endTime() != null && !request.endTime().isBlank()) {
            try {
                endTime = BulkPlanner.resolveEndTime(request.endTime(), ZonedDateTime.now());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("endTime must be \"HH:mm\" or empty.");
            }
        }
        BulkRun run = new BulkRun(kind, provider,
                request.domainId() == null ? "" : request.domainId(),
                endTime, maxPercent, maxWeeklyPercent,
                request.items().stream().map(Item::new).toList(),
                request.languages());
        current = run;
        executor.submit(() -> runLoop(run));
        return status();
    }

    /** Graceful stop: the in-flight generation always finishes first. */
    public void requestStop() {
        BulkRun run = current;
        if (run != null && !run.isFinished()) {
            run.stopRequested = true;
        }
    }

    /** @throws IllegalStateException while the run is still active */
    public synchronized void dismiss() {
        BulkRun run = current;
        if (run != null && !run.isFinished()) {
            throw new IllegalStateException("The bulk generation run is still active.");
        }
        current = null;
    }

    public StatusResponse status() {
        BulkRun run = current;
        if (run == null) {
            return new StatusResponse(false, null);
        }
        List<ItemView> items = run.items.stream()
                .map(i -> new ItemView(i.spec.id(), i.spec.label(), i.status, i.taskKey))
                .toList();
        RunView view = new RunView(run.kind, run.domainId, run.provider,
                run.endTime == null ? null : run.endTime.toString(),
                (int) run.capPercent, (int) run.weeklyCapPercent, items,
                run.currentIndex, run.phase,
                run.waitUntil == null ? null : run.waitUntil.toString(),
                run.stopRequested,
                run.startedAt.toString(),
                run.finishedAt == null ? null : run.finishedAt.toString(),
                run.utilization,
                run.resetsAt == null ? null : run.resetsAt.toString(),
                run.maxDelta,
                run.weeklyUtilization,
                run.weeklyResetsAt == null ? null : run.weeklyResetsAt.toString());
        return new StatusResponse(!run.isFinished(), view);
    }

    // --- the loop -----------------------------------------------------------

    private void runLoop(BulkRun run) {
        usage.setPassivePaused(true);
        try {
            for (int i = 0; i < run.items.size(); i++) {
                Item item = run.items.get(i);
                if (run.stopRequested) {
                    finish(run, "stopped");
                    return;
                }
                if (alreadyGenerated(run, item)) {
                    item.status = "skipped";
                    continue;
                }
                UsageBefore before = gate(run);
                if (before == null) {
                    return; // gate() already finished the run
                }
                run.currentIndex = i;
                item.status = "running";
                setPhase(run, "generating", null);
                GenerationTask task = startItem(run, item);
                if (task == null) {
                    item.status = "error";
                    continue;
                }
                item.taskKey = task.key();
                boolean terminal;
                try {
                    terminal = task.awaitTerminal(ITEM_TIMEOUT_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    item.status = "error";
                    finish(run, "stopped");
                    return;
                }
                item.status = terminal && "done".equals(task.status()) ? "done" : "error";
                if (!terminal) {
                    log.warn("Bulk item {} timed out after {} min; moving on.",
                            item.spec.id(), ITEM_TIMEOUT_MILLIS / 60_000);
                }
                measureDelta(run, before);
            }
            finish(run, "finished");
        } catch (RuntimeException e) {
            log.error("Bulk generation loop failed: {}", e.getMessage(), e);
            finish(run, "stopped");
        } finally {
            usage.setPassivePaused(false);
            if (run.finishedAt == null) {
                run.finishedAt = Instant.now();
            }
        }
    }

    /** The item may already be covered (e.g. generated manually mid-run or before). */
    private boolean alreadyGenerated(BulkRun run, Item item) {
        try {
            if ("atoms".equals(run.kind)) {
                return topics.getTopic(item.spec.id()).map(TopicDetail::hasAtoms).orElse(false);
            }
            String catalogId = item.spec.catalogId() == null ? item.spec.id() : item.spec.catalogId();
            return topics.listTopics().stream().anyMatch(t -> catalogId.equals(t.catalogId()));
        } catch (RuntimeException e) {
            log.warn("Could not re-check item {}: {}", item.spec.id(), e.getMessage());
            return false;
        }
    }

    /** Both usage windows as read just before an item started. */
    private record UsageBefore(double utilization, Double weeklyUtilization) {
    }

    /**
     * Blocks until the next item may start. Returns the usage reading it was
     * approved against, or null when the run was finished (stop/end/cap).
     */
    private UsageBefore gate(BulkRun run) {
        while (true) {
            if (run.stopRequested) {
                finish(run, "stopped");
                return null;
            }
            if (endTimePassed(run)) {
                finish(run, "endReached");
                return null;
            }
            if (!waitUntilMillis(run, usage.nextAllowedFetchAt(run.provider), "waitingPace")) {
                continue; // aborted — the loop top re-checks stop/end
            }
            BulkRefresh refresh = usage.forceRefresh(run.provider);
            if (!refresh.fresh()) {
                long retryAt = Math.max(refresh.nextAllowedAtMillis(),
                        System.currentTimeMillis() + USAGE_RETRY_MILLIS);
                waitUntilMillis(run, retryAt, "waitingUsage");
                continue;
            }
            UsageSnapshot snapshot = refresh.snapshot();
            if (snapshot == null || !snapshot.available() || snapshot.session() == null) {
                waitUntilMillis(run, System.currentTimeMillis() + USAGE_RETRY_MILLIS, "waitingUsage");
                continue;
            }
            record(run, snapshot);
            Window session = new Window(run.utilization, run.resetsAt, run.maxDelta, run.capPercent);
            Window weekly = run.weeklyUtilization == null
                    ? null
                    : new Window(run.weeklyUtilization, run.weeklyResetsAt, run.maxWeeklyDelta,
                            run.weeklyCapPercent);
            Decision decision = BulkPlanner.decide(Instant.now(), run.endTime, session, weekly);
            if (decision instanceof Proceed) {
                return new UsageBefore(run.utilization, run.weeklyUtilization);
            }
            if (decision instanceof WaitUntil wait) {
                long until = run.endTime == null
                        ? wait.until().toEpochMilli()
                        : Math.min(wait.until().toEpochMilli(), run.endTime.toEpochMilli());
                waitUntilMillis(run, until, "waitingReset");
                continue;
            }
            String reason = ((Stop) decision).reason();
            finish(run, "endTime".equals(reason) ? "endReached" : reason);
            return null;
        }
    }

    private GenerationTask startItem(BulkRun run, Item item) {
        try {
            if ("theory".equals(run.kind)) {
                String catalogId = item.spec.catalogId() == null || item.spec.catalogId().isBlank()
                        ? item.spec.id() : item.spec.catalogId().trim();
                String prompt = topicPrompts.build(new TopicGenSpec(item.spec.question(), catalogId,
                        item.spec.categoryId(), item.spec.difficulty(), item.spec.style(),
                        item.spec.styleName(), run.provider, run.languages, run.domainId));
                return generation.startOrGet("catalog:" + catalogId, run.provider, prompt,
                        AiTask.GENERATE_TOPIC);
            }
            TopicDetail topic = topics.getTopic(item.spec.id()).orElse(null);
            if (topic == null) {
                log.warn("Bulk atoms item {}: topic not found.", item.spec.id());
                return null;
            }
            return generation.startOrGet("atoms:" + topic.id(), run.provider,
                    atomsPrompts.buildFull(topic, run.provider, 1, run.languages),
                    AiTask.GENERATE_ATOMS);
        } catch (RuntimeException e) {
            log.warn("Bulk item {} could not start: {}", item.spec.id(), e.getMessage());
            return null;
        }
    }

    /** Refreshes usage after an item and records its consumed delta per window. */
    private void measureDelta(BulkRun run, UsageBefore before) {
        if (!waitUntilMillis(run, usage.nextAllowedFetchAt(run.provider), "waitingPace")) {
            return; // stop/end requested — the loop top handles it
        }
        BulkRefresh refresh = usage.forceRefresh(run.provider);
        UsageSnapshot snapshot = refresh.snapshot();
        if (!refresh.fresh() || snapshot == null || !snapshot.available() || snapshot.session() == null) {
            return; // no reading; the next gate() will fetch again
        }
        record(run, snapshot);
        double delta = BulkPlanner.consumedDelta(before.utilization(), run.utilization);
        run.maxDelta = run.maxDelta == null ? delta : Math.max(run.maxDelta, delta);
        if (before.weeklyUtilization() != null && run.weeklyUtilization != null) {
            double weeklyDelta = BulkPlanner.consumedDelta(before.weeklyUtilization(),
                    run.weeklyUtilization);
            run.maxWeeklyDelta = run.maxWeeklyDelta == null
                    ? weeklyDelta : Math.max(run.maxWeeklyDelta, weeklyDelta);
        }
    }

    /** Copies both usage windows of a fresh reading into the run's status. */
    private void record(BulkRun run, UsageSnapshot snapshot) {
        run.utilization = snapshot.session().utilization();
        run.resetsAt = BulkPlanner.parseInstant(snapshot.session().resetsAt());
        if (snapshot.weekly() != null) {
            run.weeklyUtilization = snapshot.weekly().utilization();
            run.weeklyResetsAt = BulkPlanner.parseInstant(snapshot.weekly().resetsAt());
        }
    }

    /**
     * Sleeps (in short chunks) until the target wall-clock time.
     *
     * @return true when the target was reached; false when interrupted by a stop
     *         request or the end time passing
     */
    private boolean waitUntilMillis(BulkRun run, long targetMillis, String phase) {
        while (true) {
            long now = System.currentTimeMillis();
            if (now >= targetMillis) {
                run.waitUntil = null;
                return true;
            }
            if (run.stopRequested || endTimePassed(run)) {
                run.waitUntil = null;
                return false;
            }
            setPhase(run, phase, Instant.ofEpochMilli(targetMillis));
            try {
                Thread.sleep(Math.min(WAIT_CHUNK_MILLIS, targetMillis - now));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                run.stopRequested = true;
                run.waitUntil = null;
                return false;
            }
        }
    }

    /** False in continuous mode, where there is no deadline to pass. */
    private boolean endTimePassed(BulkRun run) {
        return run.endTime != null && !Instant.now().isBefore(run.endTime);
    }

    private void setPhase(BulkRun run, String phase, Instant waitUntil) {
        run.phase = phase;
        run.waitUntil = waitUntil;
    }

    private void finish(BulkRun run, String phase) {
        setPhase(run, phase, null);
        run.finishedAt = Instant.now();
    }
}
