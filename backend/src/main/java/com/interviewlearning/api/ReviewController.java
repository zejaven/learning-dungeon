package com.interviewlearning.api;

import com.interviewlearning.lesson.LearningAtomsRepository;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.lesson.LessonDtos.ReviewDue;
import com.interviewlearning.lesson.LessonDtos.ReviewItem;
import com.interviewlearning.lesson.LessonDtos.ReviewMarkRequest;
import com.interviewlearning.lesson.LessonDtos.ReviewTopic;
import com.interviewlearning.lesson.LessonDtos.ReviewTopicPrefRequest;
import com.interviewlearning.lesson.ReviewRepository;
import com.interviewlearning.lesson.ReviewRepository.PoolRow;
import com.interviewlearning.lesson.ReviewSchedule;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.TopicSummary;
import com.interviewlearning.topics.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Global review over the practice exercises of fully completed lessons, on a
 * spaced-repetition schedule ({@link ReviewSchedule}). Each pooled exercise
 * walks a ladder of intervals and leaves the pool once the ladder is walked.
 *
 * <p>These endpoints are deliberately <b>invariant over time</b>: the list
 * carries every pooled exercise with its due and expiry instants, and the client
 * decides what is due against its own clock. That is what lets the PWA cache the
 * response without it going semantically stale, lets the per-domain filter and
 * the session cap apply after the fact, and makes offline review honest.
 *
 * <p>Stale pool rows (their exercise vanished after a lesson regeneration) are
 * pruned lazily here — but only when the topic's atoms actually loaded, see
 * {@link #resolvedRows()}.
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewRepository reviews;
    private final LearningAtomsRepository atomsRepository;
    private final TopicRepository topics;

    public ReviewController(ReviewRepository reviews,
                            LearningAtomsRepository atomsRepository,
                            TopicRepository topics) {
        this.reviews = reviews;
        this.atomsRepository = atomsRepository;
        this.topics = topics;
    }

    /** A practice exercise of the current atoms file, with the atom it belongs to. */
    private record Practice(String atomId, Exercise exercise) {
    }

    /**
     * Every pooled exercise with its position on the ladder. Includes mastered
     * ones and exercises of disabled topics: the client filters (and can offer a
     * deliberate ahead-of-schedule run over what is not due yet).
     */
    @GetMapping("/list")
    public List<ReviewItem> list() {
        return resolvedRows();
    }

    /**
     * Topics that have pooled practice exercises, with the topic total, how many
     * have walked the whole ladder and whether the topic participates. Drives the
     * review screen's left-hand tree.
     */
    @GetMapping("/topics")
    public List<ReviewTopic> topics() {
        Map<String, Boolean> prefs = reviews.topicPrefs();
        // Encounter order is topic_id-sorted (see resolvedRows); the frontend
        // re-groups these into the catalog tree, so only stable order matters.
        Map<String, int[]> counts = new LinkedHashMap<>(); // topicId -> [total, mastered]
        Map<String, List<ReviewDue>> schedule = new HashMap<>();
        Map<String, Localized> titles = new HashMap<>();
        for (ReviewItem item : resolvedRows()) {
            titles.putIfAbsent(item.topicId(), item.topicTitle());
            int[] c = counts.computeIfAbsent(item.topicId(), k -> new int[2]);
            c[0]++;
            if (item.mastered()) {
                c[1]++;
            } else {
                schedule.computeIfAbsent(item.topicId(), k -> new ArrayList<>())
                        .add(new ReviewDue(item.dueAt(), item.expiresAt()));
            }
        }
        List<ReviewTopic> out = new ArrayList<>();
        counts.forEach((topicId, c) -> out.add(new ReviewTopic(
                topicId, titles.get(topicId), c[0], c[1], prefs.getOrDefault(topicId, true),
                schedule.getOrDefault(topicId, List.of()))));
        return out;
    }

    @PostMapping("/answer")
    public ResponseEntity<Void> answer(@RequestBody ReviewMarkRequest req) {
        reviews.recordAnswer(req.topicId(), req.exerciseId(), req.correct(),
                parseInstant(req.answeredAt()), req.answerId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/topics/{topicId}")
    public ResponseEntity<Void> setTopicEnabled(@PathVariable String topicId,
                                                @RequestBody ReviewTopicPrefRequest req) {
        reviews.setTopicEnabled(topicId, req.enabled());
        return ResponseEntity.ok().build();
    }

    /**
     * Puts every exercise back on the first rung of the ladder. Destructive, and
     * the only way to undo a schedule — the settings dialog confirms first, and
     * it must not be queued offline (a wipe landing an hour later, after a
     * session, would silently discard that session).
     */
    @PostMapping("/restart")
    public ResponseEntity<Void> restartAll() {
        log.info("Resetting the whole repetition schedule at the user's request");
        reviews.restartAll();
        return ResponseEntity.ok().build();
    }

    /**
     * Every pool row resolved to its exercise against the CURRENT atoms files.
     * A row whose exercise is gone from an atoms file that DID load is deleted
     * (regeneration renamed or removed it — re-completing the lesson repopulates
     * the pool).
     *
     * <p>A topic whose atoms file could not be read at all is skipped instead:
     * its rows are left out of this response but never pruned. {@code load()}
     * returns empty for a missing file, an IO error, a parse error and an empty
     * atom list alike, and the file is rewritten in place by the AI CLI with no
     * atomic move — so a request landing mid-regeneration would otherwise wipe
     * the topic's whole pool, and with it months of repetition schedule, from a
     * plain read endpoint.
     */
    private List<ReviewItem> resolvedRows() {
        List<PoolRow> rows = reviews.pool();
        double speed = reviews.speed();
        Map<String, Optional<Map<String, Practice>>> byTopic = new HashMap<>();
        Map<String, Localized> titles = topicTitles();

        List<Long> stale = new ArrayList<>();
        List<ReviewItem> items = new ArrayList<>();
        for (PoolRow row : rows) {
            Optional<Map<String, Practice>> resolved =
                    byTopic.computeIfAbsent(row.topicId(), this::practiceExercises);
            if (resolved.isEmpty()) {
                continue;
            }
            Practice practice = resolved.get().get(row.exerciseId());
            if (practice == null) {
                stale.add(row.id());
                continue;
            }
            boolean mastered = row.graduatedAt() != null;
            items.add(new ReviewItem(
                    row.topicId(),
                    titles.getOrDefault(row.topicId(), Localized.of(row.topicId())),
                    practice.atomId(),
                    practice.exercise(),
                    row.srsStep(),
                    ReviewSchedule.stepCount(),
                    row.dueAt().toString(),
                    mastered ? null : ReviewSchedule.expiresAt(row.dueAt(), row.srsStep(), speed).toString(),
                    mastered));
        }
        if (!stale.isEmpty()) {
            log.info("Pruning {} stale review-pool row(s)", stale.size());
            reviews.deletePoolRows(stale);
        }
        return items;
    }

    /**
     * Practice exercises of the topic's current atoms file, keyed by exercise id,
     * or empty when the atoms could not be read — which callers must NOT treat as
     * "this topic has no exercises" (see {@link #resolvedRows()}).
     */
    private Optional<Map<String, Practice>> practiceExercises(String topicId) {
        Optional<LearningAtoms> atoms = atomsRepository.load(topicId);
        if (atoms.isEmpty()) {
            log.warn("Review: atoms for topic '{}' could not be read; keeping its pool rows untouched", topicId);
            return Optional.empty();
        }
        Map<String, Practice> out = new HashMap<>();
        for (Atom atom : atoms.get().atoms()) {
            if (atom.practice() == null) {
                continue;
            }
            for (Exercise ex : atom.practice()) {
                out.put(ex.id(), new Practice(atom.id(), ex));
            }
        }
        return Optional.of(out);
    }

    private Map<String, Localized> topicTitles() {
        Map<String, Localized> titles = new HashMap<>();
        try {
            for (TopicSummary t : topics.listTopics()) {
                titles.put(t.id(), t.title());
            }
        } catch (RuntimeException e) {
            log.warn("Could not list topics for review titles: {}", e.getMessage());
        }
        return titles;
    }

    /** A malformed client timestamp falls back to server time rather than failing the answer. */
    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            log.warn("Ignoring unparseable review answer time '{}'", iso);
            return null;
        }
    }
}
