package com.interviewlearning.lesson;

import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.topics.TopicDtos.BossQuestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives the lesson's unit sequence (the Duolingo-style circles) from the
 * atoms file and the boss-fight questions. Units are derived, never stored.
 *
 * <p>IMPORTANT: this algorithm is mirrored on the frontend in
 * {@code frontend/src/engine/lessonUnits.ts}; any change here must be applied
 * there too (the unit test pins the exact output).
 *
 * <ol>
 *   <li>Discovery units: one per atom with a non-empty {@code discovery} list,
 *       in file order; id {@code d:<atomId>}.</li>
 *   <li>Practice units: practice exercises flattened round-robin across atoms
 *       (1st of each atom, then 2nd, ...) so consecutive exercises mix ideas,
 *       chunked into groups of {@link #PRACTICE_CHUNK}; a trailing chunk of
 *       fewer than 3 merges into the previous one; ids {@code p1}, {@code p2}, ...</li>
 *   <li>Boss units: one per boss-fight question, id {@code b:<questionId>}.</li>
 * </ol>
 */
public final class LessonUnits {

    public static final int PRACTICE_CHUNK = 5;
    private static final int MIN_LAST_CHUNK = 3;

    private LessonUnits() {
    }

    /** One derived unit; boss units carry no exercise ids (the question id is in the unit id). */
    public record UnitRef(String unitId, String kind, List<String> exerciseIds) {
    }

    public static List<UnitRef> derive(LearningAtoms atoms, List<BossQuestion> bossFight) {
        List<UnitRef> units = new ArrayList<>();
        List<Atom> all = atoms.atoms() == null ? List.of() : atoms.atoms();

        for (Atom atom : all) {
            List<Exercise> discovery = atom.discovery();
            if (discovery != null && !discovery.isEmpty()) {
                units.add(new UnitRef("d:" + atom.id(), "discovery",
                        discovery.stream().map(Exercise::id).toList()));
            }
        }

        List<String> flat = roundRobinPractice(all);
        List<List<String>> chunks = chunk(flat);
        for (int i = 0; i < chunks.size(); i++) {
            units.add(new UnitRef("p" + (i + 1), "practice", List.copyOf(chunks.get(i))));
        }

        for (BossQuestion q : bossFight) {
            units.add(new UnitRef("b:" + q.id(), "boss", List.of()));
        }
        return units;
    }

    private static List<String> roundRobinPractice(List<Atom> atoms) {
        int longest = atoms.stream()
                .mapToInt(a -> a.practice() == null ? 0 : a.practice().size())
                .max().orElse(0);
        List<String> flat = new ArrayList<>();
        for (int round = 0; round < longest; round++) {
            for (Atom atom : atoms) {
                List<Exercise> practice = atom.practice();
                if (practice != null && round < practice.size()) {
                    flat.add(practice.get(round).id());
                }
            }
        }
        return flat;
    }

    private static List<List<String>> chunk(List<String> flat) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < flat.size(); i += PRACTICE_CHUNK) {
            chunks.add(new ArrayList<>(flat.subList(i, Math.min(i + PRACTICE_CHUNK, flat.size()))));
        }
        if (chunks.size() > 1 && chunks.get(chunks.size() - 1).size() < MIN_LAST_CHUNK) {
            List<String> tail = chunks.remove(chunks.size() - 1);
            chunks.get(chunks.size() - 1).addAll(tail);
        }
        return chunks;
    }
}
