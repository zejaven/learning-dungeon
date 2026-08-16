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
                new BossQuestion("q-one", Localized.of("?")),
                new BossQuestion("q-two", Localized.of("?")));

        List<UnitRef> units = LessonUnits.derive(atoms, boss);

        // Round-robin practice: a1 b1 c1 | a2 c2 a3 → one chunk of 5 + trailing 1
        // merges into the previous chunk (last chunk < 3). A single mistakes
        // unit sits between practice and the boss units.
        assertEquals(List.of(
                new UnitRef("d:a", "discovery", List.of("a-d1")),
                new UnitRef("d:b", "discovery", List.of("b-d1", "b-d2")),
                new UnitRef("p1", "practice", List.of("a1", "b1", "c1", "a2", "c2", "a3")),
                new UnitRef("mistakes", "mistakes", List.of()),
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
                new UnitRef("p2", "practice", List.of("e6", "e7", "e8", "e9", "e10", "e11", "e12")),
                new UnitRef("mistakes", "mistakes", List.of())
        ), units);
    }

    @Test
    void capstonePracticeIsAFinalBlockAfterRegularPractice() {
        // Two regular atoms + one capstone. Capstone practice is NOT round-robined;
        // it forms dedicated c-units after the regular p-units, before mistakes/boss.
        LearningAtoms atoms = new LearningAtoms(1, "demo", 1, "claude", "", List.of(
                atom("a", List.of(ex("a-d1")), List.of(ex("a1"), ex("a2"))),
                atom("b", List.of(ex("b-d1")), List.of(ex("b1"), ex("b2"))),
                atom("cap", List.of(ex("cap-d1")), List.of(ex("s1"), ex("s2")), true)));
        List<BossQuestion> boss = List.of(new BossQuestion("q", Localized.of("?")));

        List<UnitRef> units = LessonUnits.derive(atoms, boss);

        assertEquals(List.of(
                new UnitRef("d:a", "discovery", List.of("a-d1")),
                new UnitRef("d:b", "discovery", List.of("b-d1")),
                new UnitRef("d:cap", "discovery", List.of("cap-d1")),
                // regular practice round-robin: a1 b1 a2 b2
                new UnitRef("p1", "practice", List.of("a1", "b1", "a2", "b2")),
                // capstone practice as its own final block
                new UnitRef("c1", "capstone", List.of("s1", "s2")),
                new UnitRef("mistakes", "mistakes", List.of()),
                new UnitRef("b:q", "boss", List.of())
        ), units);
    }

    private static Atom atom(String id, List<Exercise> discovery, List<Exercise> practice) {
        return atom(id, discovery, practice, false);
    }

    private static Atom atom(String id, List<Exercise> discovery, List<Exercise> practice, boolean capstone) {
        Localized text = Localized.of(id);
        return new Atom(id, text, text, discovery, practice, capstone);
    }

    private static Exercise ex(String id) {
        // Positional: id, type, prompt, code, codeLang, mermaid, reveal, feedback,
        // options, answer, text, answers, blanks, tokens, distractors, steps, pairs.
        return new Exercise(id, "true_false", Localized.of("?"),
                null, null, null, null, null, null, Boolean.TRUE, null, null, null, null, null, null, null);
    }
}
