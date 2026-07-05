package com.interviewlearning.lesson;

import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.lesson.LessonUnits.UnitRef;
import com.interviewlearning.topics.TopicDtos.BossQuestion;
import com.interviewlearning.topics.TopicDtos.Localized;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the exact unit derivation. The same algorithm is mirrored on the
 * frontend in {@code frontend/src/engine/lessonUnits.ts}; if this test needs a
 * change, the mirror needs the same change.
 */
class LessonUnitsTest {

    @Test
    void derivesDiscoveryPracticeAndBossUnits() {
        // 3 atoms with 1/2/3 discovery-free practice spreads:
        // a: practice a1 a2 a3, b: practice b1, c: practice c1 c2
        LearningAtoms atoms = new LearningAtoms(1, "demo", 1, "claude", "", List.of(
                atom("a", List.of(ex("a-d1")), List.of(ex("a1"), ex("a2"), ex("a3"))),
                atom("b", List.of(ex("b-d1"), ex("b-d2")), List.of(ex("b1"))),
                atom("c", List.of(), List.of(ex("c1"), ex("c2")))
        ));
        List<BossQuestion> boss = List.of(
                new BossQuestion("q-one", new Localized("?", "?")),
                new BossQuestion("q-two", new Localized("?", "?")));

        List<UnitRef> units = LessonUnits.derive(atoms, boss);

        // Round-robin practice: a1 b1 c1 | a2 c2 a3 → one chunk of 5 + trailing 1
        // merges into the previous chunk (last chunk < 3).
        assertEquals(List.of(
                new UnitRef("d:a", "discovery", List.of("a-d1")),
                new UnitRef("d:b", "discovery", List.of("b-d1", "b-d2")),
                new UnitRef("p1", "practice", List.of("a1", "b1", "c1", "a2", "c2", "a3")),
                new UnitRef("b:q-one", "boss", List.of()),
                new UnitRef("b:q-two", "boss", List.of())
        ), units);
    }

    @Test
    void chunksPracticeIntoGroupsOfFive() {
        // 12 practice exercises in one atom → p1(5), p2(5), p3(2) → p3 merges into p2.
        List<Exercise> practice = List.of(
                ex("e1"), ex("e2"), ex("e3"), ex("e4"), ex("e5"), ex("e6"),
                ex("e7"), ex("e8"), ex("e9"), ex("e10"), ex("e11"), ex("e12"));
        LearningAtoms atoms = new LearningAtoms(1, "demo", 1, "claude", "", List.of(
                atom("a", List.of(ex("d1")), practice)));

        List<UnitRef> units = LessonUnits.derive(atoms, List.of());

        assertEquals(List.of(
                new UnitRef("d:a", "discovery", List.of("d1")),
                new UnitRef("p1", "practice", List.of("e1", "e2", "e3", "e4", "e5")),
                new UnitRef("p2", "practice", List.of("e6", "e7", "e8", "e9", "e10", "e11", "e12"))
        ), units);
    }

    private static Atom atom(String id, List<Exercise> discovery, List<Exercise> practice) {
        Localized text = new Localized(id, id);
        return new Atom(id, text, text, discovery, practice);
    }

    private static Exercise ex(String id) {
        return new Exercise(id, "true_false", new Localized("?", "?"),
                null, null, null, null, null, Boolean.TRUE, null, null, null, null, null, null);
    }
}
