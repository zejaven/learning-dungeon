package com.interviewlearning.lesson;

import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.lesson.ReviewRepository.Unenrolled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides when an exercise joins spaced repetition: the moment it is answered in
 * a lesson, right or wrong. Waiting for the whole lesson — Boss Fight included —
 * left everything out of review for a topic the learner stopped halfway through
 * or never fought the boss of, which is most of what gets answered.
 *
 * <p>Only PRACTICE exercises enroll. Discovery exercises are prediction-first:
 * they are asked before the concept is taught and lean on their reveal card,
 * whereas practice exercises are written to be answerable standalone precisely
 * because review shows them out of context (see prompts/generate-learning-atoms.md).
 * Whether an id is practice is read from the current atoms file, never trusted
 * from the request.
 */
@Component
public class ReviewEnrollment {

    private static final Logger log = LoggerFactory.getLogger(ReviewEnrollment.class);

    private final LearningAtomsRepository atomsRepository;
    private final ReviewRepository reviews;

    public ReviewEnrollment(LearningAtomsRepository atomsRepository, ReviewRepository reviews) {
        this.atomsRepository = atomsRepository;
        this.reviews = reviews;
    }

    /**
     * Enrolls the exercise if it is a practice exercise of the topic's current
     * atoms file. Callers pass lesson answers only — a review answer is already
     * enrolled by definition.
     */
    public void onLessonAnswer(String topicId, String exerciseId, Instant answeredAt) {
        atomsRepository.load(topicId)
                .flatMap(atoms -> practiceAtomOf(atoms, exerciseId))
                .ifPresent(atomId -> reviews.enroll(topicId, exerciseId, atomId, answeredAt));
    }

    /**
     * Catches up on practice answers given before enrollment moved to answer
     * time. Idempotent — it only ever inserts exercises that have no pool row —
     * so it simply runs on every start, which also covers an answer whose
     * enrollment was skipped because its atoms file was mid-rewrite.
     *
     * <p>They are treated as answered now rather than at their stored time: an
     * answer from weeks ago would otherwise come back already lapsed, flagged as
     * a missed window the learner never had.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void enrollEarlierAnswers() {
        try {
            List<Unenrolled> pending = reviews.unenrolledLessonAnswers();
            if (pending.isEmpty()) {
                return;
            }
            Map<String, Optional<LearningAtoms>> atomsByTopic = new HashMap<>();
            Instant now = Instant.now();
            int enrolled = 0;
            for (Unenrolled u : pending) {
                Optional<String> atomId = atomsByTopic
                        .computeIfAbsent(u.topicId(), atomsRepository::load)
                        .flatMap(atoms -> practiceAtomOf(atoms, u.exerciseId()));
                if (atomId.isPresent() && reviews.enroll(u.topicId(), u.exerciseId(), atomId.get(), now)) {
                    enrolled++;
                }
            }
            if (enrolled > 0) {
                log.info("Enrolled {} earlier-answered practice exercise(s) in spaced repetition", enrolled);
            }
        } catch (RuntimeException e) {
            // Never block startup over review bookkeeping; the next start retries.
            log.warn("Could not enroll earlier lesson answers in review: {}", e.getMessage());
        }
    }

    /** The atom id when {@code exerciseId} is one of the file's practice exercises. */
    static Optional<String> practiceAtomOf(LearningAtoms atoms, String exerciseId) {
        if (atoms.atoms() == null || exerciseId == null) {
            return Optional.empty();
        }
        for (Atom atom : atoms.atoms()) {
            if (atom.practice() == null) {
                continue;
            }
            for (Exercise ex : atom.practice()) {
                if (exerciseId.equals(ex.id())) {
                    return Optional.of(atom.id());
                }
            }
        }
        return Optional.empty();
    }
}
