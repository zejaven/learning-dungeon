package com.interviewlearning.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.lesson.LearningAtomsRepository;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.lesson.LessonDtos.ReviewAnswerRequest;
import com.interviewlearning.lesson.LessonDtos.ReviewAnswerResponse;
import com.interviewlearning.lesson.LessonDtos.ReviewItem;
import com.interviewlearning.lesson.LessonDtos.ReviewSessionDto;
import com.interviewlearning.lesson.LessonDtos.ReviewSummary;
import com.interviewlearning.lesson.ReviewRepository;
import com.interviewlearning.lesson.ReviewRepository.PoolRow;
import com.interviewlearning.lesson.ReviewRepository.SessionRow;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Global review over the practice exercises of fully completed lessons. The
 * pool is explicit rows in review_pool; a session snapshots the resolved
 * exercises into state_json (so the review screen never loads topics) with a
 * requeue queue: wrong answers re-enter at the tail until answered correctly.
 * Stale pool rows (their exercise vanished after a lesson regeneration) are
 * pruned lazily here.
 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewRepository reviews;
    private final LearningAtomsRepository atomsRepository;
    private final TopicRepository topics;
    private final ObjectMapper mapper;

    public ReviewController(ReviewRepository reviews,
                            LearningAtomsRepository atomsRepository,
                            TopicRepository topics,
                            ObjectMapper mapper) {
        this.reviews = reviews;
        this.atomsRepository = atomsRepository;
        this.topics = topics;
        this.mapper = mapper;
    }

    /** Session state persisted as review_session.state_json. */
    record SessionState(List<ReviewItem> items, List<Integer> queue, int position) {
    }

    @GetMapping("/summary")
    public ReviewSummary summary() {
        List<ReviewItem> pool = resolvedPool();
        long topicCount = pool.stream().map(ReviewItem::topicId).distinct().count();
        return new ReviewSummary(pool.size(), (int) topicCount);
    }

    @PostMapping("/session/start")
    public ResponseEntity<ReviewSessionDto> start() {
        Optional<SessionRow> active = reviews.activeSession();
        if (active.isPresent()) {
            Optional<ReviewSessionDto> resumed = dto(active.get());
            if (resumed.isPresent()) {
                return ResponseEntity.ok(resumed.get());
            }
            // dto() closed an unreadable session; fall through to a fresh one.
        }
        List<ReviewItem> items = resolvedPool();
        Collections.shuffle(items);
        List<Integer> queue = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            queue.add(i);
        }
        SessionState state = new SessionState(items, queue, 0);
        long id = reviews.insertSession(write(state));
        if (items.isEmpty()) {
            // An empty session is already finished; close it now so it never
            // lingers as the "active" session and masks a later-populated pool.
            reviews.finishSession(id);
        }
        return ResponseEntity.ok(new ReviewSessionDto(id, items, queue, 0, items.isEmpty()));
    }

    @GetMapping("/session/active")
    public ResponseEntity<ReviewSessionDto> active() {
        return reviews.activeSession()
                .flatMap(this::dto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/session/{id}/answer")
    public ResponseEntity<ReviewAnswerResponse> answer(@PathVariable long id,
                                                       @RequestBody ReviewAnswerRequest req) {
        SessionRow row = reviews.session(id).orElse(null);
        SessionState state = row == null ? null : read(row.stateJson());
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        if (req.itemIndex() < 0 || req.itemIndex() >= state.items().size()) {
            return ResponseEntity.badRequest().build();
        }
        ReviewItem item = state.items().get(req.itemIndex());
        reviews.bumpStats(item.topicId(), item.exercise().id(), req.correct());

        List<Integer> queue = new ArrayList<>(state.queue());
        int position = state.position() + 1;
        if (!req.correct()) {
            queue.add(req.itemIndex()); // solve again later in this session
        }
        boolean finished = position >= queue.size();
        reviews.updateSessionState(id, write(new SessionState(state.items(), queue, position)));
        if (finished) {
            reviews.finishSession(id);
        }
        return ResponseEntity.ok(new ReviewAnswerResponse(queue, position, finished));
    }

    @PostMapping("/session/{id}/abandon")
    public ResponseEntity<Void> abandon(@PathVariable long id) {
        reviews.finishSession(id);
        return ResponseEntity.ok().build();
    }

    /**
     * The pool resolved to full exercises against the CURRENT atoms files;
     * rows that no longer resolve are deleted (regeneration renamed or removed
     * them — re-completing the lesson repopulates the pool).
     */
    private List<ReviewItem> resolvedPool() {
        List<PoolRow> rows = reviews.pool();
        Map<String, Map<String, ReviewItem>> byTopic = new HashMap<>();
        Map<String, Localized> titles = topicTitles();

        List<Long> stale = new ArrayList<>();
        List<ReviewItem> items = new ArrayList<>();
        for (PoolRow row : rows) {
            Map<String, ReviewItem> resolved = byTopic.computeIfAbsent(row.topicId(),
                    topicId -> practiceExercises(topicId, titles.getOrDefault(topicId, Localized.of(topicId))));
            ReviewItem item = resolved.get(row.exerciseId());
            if (item == null) {
                stale.add(row.id());
            } else {
                items.add(item);
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

    private Optional<ReviewSessionDto> dto(SessionRow row) {
        SessionState state = read(row.stateJson());
        if (state == null) {
            // Unreadable state (schema drift): close the broken session so a
            // fresh one can start.
            reviews.finishSession(row.id());
            return Optional.empty();
        }
        boolean finished = state.position() >= state.queue().size();
        if (finished) {
            // A logically-finished session (empty pool, or every item answered)
            // must not resurface as resumable — close it and let the caller
            // start a fresh session over the current pool.
            reviews.finishSession(row.id());
            return Optional.empty();
        }
        return Optional.of(new ReviewSessionDto(
                row.id(), state.items(), state.queue(), state.position(), finished));
    }

    private String write(SessionState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize review session", e);
        }
    }

    private SessionState read(String json) {
        try {
            return mapper.readValue(json, SessionState.class);
        } catch (JsonProcessingException e) {
            log.warn("Could not parse review session state: {}", e.getMessage());
            return null;
        }
    }
}
