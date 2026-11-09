package com.interviewlearning.lesson;

import com.interviewlearning.lesson.LessonDtos.Atom;
import com.interviewlearning.lesson.LessonDtos.Exercise;
import com.interviewlearning.lesson.LessonDtos.LearningAtoms;
import com.interviewlearning.topics.TopicDtos.BossQuestion;

import java.util.ArrayList;
import java.util.Comparator;
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
 *   <li>Practice units: practice exercises of the NON-capstone atoms flattened
 *       round-robin (1st of each atom, then 2nd, ...) so consecutive exercises
 *       mix ideas, chunked into groups of {@link #PRACTICE_CHUNK}; a trailing
 *       chunk of fewer than 3 merges into the previous one; ids {@code p1}, ...</li>
 *   <li>Capstone units: the practice of {@code capstone: true} atoms (synthesis
 *       exercises) as a dedicated final block after regular practice, in file
 *       order, chunked; ids {@code c1}, {@code c2}, ... — the difficulty peak
 *       right before the Boss Fight.</li>
 *   <li>Mistakes unit: a single {@code mistakes} unit after practice and before
 *       the boss units. Its contents are dynamic (the exercises the learner
 *       answered wrong, computed on the client from saved answers), so it
 *       carries no exercise ids here; lesson completion ignores it.</li>
 *   <li>Boss units: one per boss-fight question, id {@code b:<questionId>}.</li>
 * </ol>
 *
 * <p>Code tasks join the chain as {@code task} units, id {@code t:<taskId>}, no
 * exercise ids. Practice tasks (sorted by order, then id) are spread over the
 * practice chunks: task k of n goes after chunk {@code ceil(k*N/(n+1))} of N,
 * or after the discovery units when there is no practice. The final task sits
 * right before the boss units, after the mistakes unit.
 */
public final class LessonUnits {

    public static final int PRACTICE_CHUNK = 5;
    private static final int MIN_LAST_CHUNK = 3;

    private LessonUnits() {
    }

    /** One derived unit; boss and task units carry no exercise ids (the id is in the unit id). */
    public record UnitRef(String unitId, String kind, List<String> exerciseIds) {
    }

    /** What the chain needs of a code task. {@code kind} is "practice" or "final". */
    public record TaskRef(String id, String kind, int order) {
    }

    public static List<UnitRef> derive(LearningAtoms atoms, List<BossQuestion> bossFight) {
        return derive(atoms, bossFight, List.of());
    }

    public static List<UnitRef> derive(LearningAtoms atoms, List<BossQuestion> bossFight, List<TaskRef> tasks) {
        List<TaskRef> sorted = tasks.stream()
                .sorted(Comparator.comparingInt(TaskRef::order).thenComparing(TaskRef::id))
                .toList();
        List<TaskRef> practiceTasks = sorted.stream().filter(t -> !"final".equals(t.kind())).toList();
        List<TaskRef> finalTasks = sorted.stream().filter(t -> "final".equals(t.kind())).toList();
        List<UnitRef> units = new ArrayList<>();
        List<Atom> all = atoms.atoms() == null ? List.of() : atoms.atoms();

        for (Atom atom : all) {
            List<Exercise> discovery = atom.discovery();
            if (discovery != null && !discovery.isEmpty()) {
                units.add(new UnitRef("d:" + atom.id(), "discovery",
                        discovery.stream().map(Exercise::id).toList()));
            }
        }

        List<Atom> regular = all.stream().filter(a -> !a.capstone()).toList();
        List<Atom> capstones = all.stream().filter(Atom::capstone).toList();

        List<List<String>> chunks = chunk(roundRobinPractice(regular));
        int n = practiceTasks.size();
        int chunkCount = chunks.size();
        if (chunkCount == 0) {
            practiceTasks.forEach(t -> units.add(taskUnit(t)));
        }
        for (int i = 0; i < chunkCount; i++) {
            units.add(new UnitRef("p" + (i + 1), "practice", List.copyOf(chunks.get(i))));
            for (int k = 1; k <= n; k++) {
                if (slot(k, n, chunkCount) == i + 1) {
                    units.add(taskUnit(practiceTasks.get(k - 1)));
                }
            }
        }

        // Capstone synthesis: the capstone atoms' practice, in file order, as a
        // dedicated final block right before the mistakes/boss units.
        List<String> capFlat = new ArrayList<>();
        for (Atom atom : capstones) {
            if (atom.practice() != null) {
                atom.practice().forEach(e -> capFlat.add(e.id()));
            }
        }
        List<List<String>> capChunks = chunk(capFlat);
        for (int i = 0; i < capChunks.size(); i++) {
            units.add(new UnitRef("c" + (i + 1), "capstone", List.copyOf(capChunks.get(i))));
        }

        // Single "fix your mistakes" unit: contents are derived on the client
        // from the learner's wrong answers, so no exercise ids here.
        units.add(new UnitRef("mistakes", "mistakes", List.of()));

        // The final task covers the whole topic: the last step before the Boss Fight.
        finalTasks.forEach(t -> units.add(taskUnit(t)));

        for (BossQuestion q : bossFight) {
            units.add(new UnitRef("b:" + q.id(), "boss", List.of()));
        }
        return units;
    }

    private static UnitRef taskUnit(TaskRef task) {
        return new UnitRef("t:" + task.id(), "task", List.of());
    }

    /** The practice chunk (1-based) that task k of n follows: evenly spread over the chunks. */
    private static int slot(int k, int n, int chunkCount) {
        int ceil = (k * chunkCount + n) / (n + 1);
        return Math.max(1, Math.min(chunkCount, ceil));
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
