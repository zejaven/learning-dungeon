package com.interviewlearning.api;

import com.interviewlearning.lesson.LearningAtomsRepository;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.ExerciseAnswerRequest;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.lesson.LessonDtos.LessonState;
import com.interviewlearning.lesson.LessonDtos.RecomputeResponse;
import com.interviewlearning.lesson.LessonProgressRepository;
import com.interviewlearning.lesson.LessonProgressRepository.PoolEntry;
import com.interviewlearning.topics.TopicDtos.BossQuestion;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves the "Learn by micro-actions" lesson content and progress. Exercise
 * grading is deterministic and happens on the frontend; unit and lesson
 * completion are DERIVED from which exercises are answered (see
 * {@link LessonProgressRepository}), so these endpoints only persist answers
 * and recompute lesson completion from stable ids.
 */
@RestController
public class LessonController {

    private final LearningAtomsRepository atomsRepository;
    private final LessonProgressRepository progress;
    private final TopicRepository topics;

    public LessonController(LearningAtomsRepository atomsRepository,
                            LessonProgressRepository progress,
                            TopicRepository topics) {
        this.atomsRepository = atomsRepository;
        this.progress = progress;
        this.topics = topics;
    }

    /** 404 when the topic has no (valid) learning-atoms.json yet. */
    @GetMapping("/api/topics/{id}/atoms")
    public ResponseEntity<LearningAtoms> atoms(@PathVariable String id) {
        return atomsRepository.load(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Progress: all saved answers (the client derives unit/lesson state from them). */
    @GetMapping("/api/lesson/{topicId}/state")
    public ResponseEntity<LessonState> state(@PathVariable String topicId) {
        if (atomsRepository.load(topicId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new LessonState(
                progress.isLessonCompleted(topicId), progress.lessonAnswers(topicId)));
    }

    /** Append-only answer log (upserted per exercise); correctness is trusted from the client. */
    @PostMapping("/api/lesson/{topicId}/answer")
    public ResponseEntity<Void> answer(@PathVariable String topicId,
                                       @RequestBody ExerciseAnswerRequest req) {
        progress.recordAnswer(topicId, req);
        return ResponseEntity.ok().build();
    }

    /**
     * Recomputes lesson completion from the persisted answers + boss passes
     * (all discovery/practice exercises answered AND all boss questions passed).
     * On completion the topic's practice exercises join the global review pool.
     */
    @PostMapping("/api/lesson/{topicId}/recompute")
    public ResponseEntity<RecomputeResponse> recompute(@PathVariable String topicId) {
        LearningAtoms atoms = atomsRepository.load(topicId).orElse(null);
        if (atoms == null) {
            return ResponseEntity.notFound().build();
        }
        List<String> bossQids = topics.getTopic(topicId)
                .map(t -> t.bossFight().stream().map(BossQuestion::id).toList())
                .orElse(List.of());
        boolean completed = progress.recomputeLessonCompletion(
                topicId, requiredExerciseIds(atoms), bossQids, practicePool(atoms));
        return ResponseEntity.ok(new RecomputeResponse(completed));
    }

    /** Every discovery + practice exercise id — all of these must be answered to complete. */
    private static List<String> requiredExerciseIds(LearningAtoms atoms) {
        List<String> ids = new ArrayList<>();
        for (Atom atom : atoms.atoms()) {
            for (Exercise ex : allExercises(atom)) {
                ids.add(ex.id());
            }
        }
        return ids;
    }

    /** All practice exercises of the file — the topic's contribution to the review pool. */
    private static List<PoolEntry> practicePool(LearningAtoms atoms) {
        List<PoolEntry> pool = new ArrayList<>();
        for (Atom atom : atoms.atoms()) {
            if (atom.practice() == null) {
                continue;
            }
            for (Exercise ex : atom.practice()) {
                pool.add(new PoolEntry(ex.id(), atom.id()));
            }
        }
        return pool;
    }

    private static List<Exercise> allExercises(Atom atom) {
        List<Exercise> all = new ArrayList<>();
        if (atom.discovery() != null) all.addAll(atom.discovery());
        if (atom.practice() != null) all.addAll(atom.practice());
        return all;
    }
}
