package com.interviewlearning.api;

import com.interviewlearning.lesson.LearningAtomsRepository;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.lesson.LessonDtos.ReviewItem;
import com.interviewlearning.lesson.LessonDtos.ReviewMarkRequest;
import com.interviewlearning.lesson.LessonDtos.ReviewTopic;
import com.interviewlearning.lesson.LessonDtos.ReviewTopicPrefRequest;
import com.interviewlearning.lesson.ReviewRepository;
import com.interviewlearning.lesson.ReviewRepository.PoolRow;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Global review over the practice exercises of fully completed lessons. Whether
 * an exercise is in the review list is persistent per-exercise state
 * ({@code review_pool.pending}), not a session snapshot: answering it correctly
 * removes it, a wrong answer keeps it, and a per-topic or global "start again"
 * returns answered ones. A per-topic {@code enabled} flag hides/shows a topic's
 * pending exercises without forgetting which ones were pending. Stale pool rows
 * (their exercise vanished after a lesson regeneration) are pruned lazily here.
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

    /** A resolved pool row: its exercise (against current atoms) plus its pending flag. */
    private record Resolved(ReviewItem item, boolean pending) {
    }

    /**
     * The current review list: pending exercises of enabled topics, resolved to
     * full exercises. The client shuffles these and requeues wrong answers.
     */
    @GetMapping("/list")
    public List<ReviewItem> list() {
        return reviewList();
    }

    /**
     * Topics that have pooled practice exercises, each with how many are still in
     * the list ({@code pending}), the topic total and whether it participates.
     * Drives the review screen's left-hand tree.
     */
    @GetMapping("/topics")
    public List<ReviewTopic> topics() {
        Map<String, Boolean> prefs = reviews.topicPrefs();
        // Encounter order is topic_id-sorted (see resolvedRows); the frontend
        // re-groups these into the catalog tree, so only stable order matters.
        Map<String, int[]> counts = new LinkedHashMap<>(); // topicId -> [pending, total]
        Map<String, Localized> titles = new HashMap<>();
        for (Resolved r : resolvedRows()) {
            String topicId = r.item().topicId();
            titles.putIfAbsent(topicId, r.item().topicTitle());
            int[] c = counts.computeIfAbsent(topicId, k -> new int[2]);
            if (r.pending()) {
                c[0]++;
            }
            c[1]++;
        }
        List<ReviewTopic> out = new ArrayList<>();
        counts.forEach((topicId, c) -> out.add(new ReviewTopic(
                topicId, titles.get(topicId), c[0], c[1], prefs.getOrDefault(topicId, true))));
        return out;
    }

    @PostMapping("/answer")
    public ResponseEntity<Void> answer(@RequestBody ReviewMarkRequest req) {
        reviews.recordAnswer(req.topicId(), req.exerciseId(), req.correct());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/topics/{topicId}")
    public ResponseEntity<Void> setTopicEnabled(@PathVariable String topicId,
                                                @RequestBody ReviewTopicPrefRequest req) {
        reviews.setTopicEnabled(topicId, req.enabled());
        return ResponseEntity.ok().build();
    }

    /** Per-topic "start again": returns all of the topic's answered exercises to the list. */
    @PostMapping("/topics/{topicId}/restart")
    public ResponseEntity<Void> restartTopic(@PathVariable String topicId) {
        reviews.restartTopic(topicId);
        return ResponseEntity.ok().build();
    }

    /** Global "start again": returns every answered exercise, across all topics, to the list. */
    @PostMapping("/restart")
    public ResponseEntity<Void> restartAll() {
        reviews.restartAll();
        return ResponseEntity.ok().build();
    }

    /** The pending exercises of enabled topics, resolved to full exercises. */
    private List<ReviewItem> reviewList() {
        Map<String, Boolean> prefs = reviews.topicPrefs();
        List<ReviewItem> out = new ArrayList<>();
        for (Resolved r : resolvedRows()) {
            if (r.pending() && prefs.getOrDefault(r.item().topicId(), true)) {
                out.add(r.item());
            }
        }
        return out;
    }

    /**
     * Every pool row resolved to its exercise against the CURRENT atoms files,
     * carrying its pending flag. Rows that no longer resolve are deleted
     * (regeneration renamed or removed them — re-completing the lesson
     * repopulates the pool).
     */
    private List<Resolved> resolvedRows() {
        List<PoolRow> rows = reviews.pool();
        Map<String, Map<String, ReviewItem>> byTopic = new HashMap<>();
        Map<String, Localized> titles = topicTitles();

        List<Long> stale = new ArrayList<>();
        List<Resolved> items = new ArrayList<>();
        for (PoolRow row : rows) {
            Map<String, ReviewItem> resolved = byTopic.computeIfAbsent(row.topicId(),
                    topicId -> practiceExercises(topicId, titles.getOrDefault(topicId, Localized.of(topicId))));
            ReviewItem item = resolved.get(row.exerciseId());
            if (item == null) {
                stale.add(row.id());
            } else {
                items.add(new Resolved(item, row.pending()));
            }
        }
        if (!stale.isEmpty()) {
            log.info("Pruning {} stale review-pool row(s)", stale.size());
            reviews.deletePoolRows(stale);
        }
        return items;
    }

    /** Practice exercises of the topic's current atoms file, keyed by exercise id. */
    private Map<String, ReviewItem> practiceExercises(String topicId, Localized title) {
        Map<String, ReviewItem> out = new HashMap<>();
        Optional<LearningAtoms> atoms = atomsRepository.load(topicId);
        if (atoms.isEmpty()) {
            return out;
        }
        for (Atom atom : atoms.get().atoms()) {
            if (atom.practice() == null) {
                continue;
            }
            for (Exercise ex : atom.practice()) {
                out.put(ex.id(), new ReviewItem(topicId, title, atom.id(), ex));
            }
        }
        return out;
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
}
