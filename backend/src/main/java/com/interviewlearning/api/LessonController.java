package com.interviewlearning.api;

import com.interviewlearning.lesson.LearningAtomsRepository;
import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.AtomsResponse;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.ExerciseAnswerRequest;
import com.interviewlearning.lesson.LessonDtos.LessonState;
import com.interviewlearning.lesson.LessonDtos.UnitCompleteRequest;
import com.interviewlearning.lesson.LessonDtos.UnitCompleteResponse;
import com.interviewlearning.lesson.LessonProgressRepository;
import com.interviewlearning.lesson.LessonProgressRepository.PoolEntry;
import com.interviewlearning.lesson.LessonUnits;
import com.interviewlearning.lesson.LessonUnits.UnitRef;
import com.interviewlearning.topics.TopicDtos.BossQuestion;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.http.HttpStatus;
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
 * grading is deterministic and happens on the frontend; endpoints here only
 * persist results and recompute unit/lesson completion.
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
    public ResponseEntity<AtomsResponse> atoms(@PathVariable String id) {
        return atomsRepository.load(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Progress for the CURRENT atoms file. Unit rows written against an older
     * hash simply do not match, so a regenerated lesson reports as fresh.
     */
    @GetMapping("/api/lesson/{topicId}/state")
    public ResponseEntity<LessonState> state(@PathVariable String topicId) {
        return atomsRepository.load(topicId)
                .map(atoms -> new LessonState(
                        atoms.atomsHash(),
                        progress.completedUnits(topicId, atoms.atomsHash()),
                        progress.isLessonCompleted(topicId, atoms.atomsHash()),
                        progress.lessonAnswers(topicId, atoms.atomsHash())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Append-only answer log; correctness is computed by the frontend and trusted. */
    @PostMapping("/api/lesson/{topicId}/answer")
    public ResponseEntity<Void> answer(@PathVariable String topicId,
                                       @RequestBody ExerciseAnswerRequest req) {
        progress.recordAnswer(topicId, req);
        return ResponseEntity.ok().build();
    }

    /**
     * Marks a unit complete and recomputes lesson completion. 409 when the
     * client answered against a stale atoms file (regenerated meanwhile).
     */
    @PostMapping("/api/lesson/{topicId}/unit-complete")
    public ResponseEntity<UnitCompleteResponse> unitComplete(@PathVariable String topicId,
                                                             @RequestBody UnitCompleteRequest req) {
        AtomsResponse atoms = atomsRepository.load(topicId).orElse(null);
        if (atoms == null) {
            return ResponseEntity.notFound().build();
        }
        if (!atoms.atomsHash().equals(req.atomsHash())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        progress.completeUnit(topicId, req.unitId(), atoms.atomsHash());

        List<BossQuestion> bossFight = topics.getTopic(topicId)
                .map(t -> t.bossFight())
                .orElse(List.of());
        List<UnitRef> units = LessonUnits.derive(atoms.atoms(), bossFight);
        boolean completed = progress.recomputeLessonCompletion(
                topicId, atoms.atomsHash(), units, practicePool(atoms));
        return ResponseEntity.ok(new UnitCompleteResponse(completed));
    }

    /** All practice exercises of the file — the topic's contribution to the review pool. */
    private static List<PoolEntry> practicePool(AtomsResponse atoms) {
        List<PoolEntry> pool = new ArrayList<>();
        for (Atom atom : atoms.atoms().atoms()) {
            if (atom.practice() == null) {
                continue;
            }
            for (Exercise ex : atom.practice()) {
                pool.add(new PoolEntry(ex.id(), atom.id()));
            }
        }
        return pool;
    }
}
