import { create } from 'zustand';
import { useLang } from '../i18n';
import {
  fetchAtoms,
  fetchLessonState,
  recomputeLesson,
  saveExerciseAnswer,
} from './api';
import { grade, shuffled } from './grading';
import { pendingAnswers } from './offline/outbox';
import type { AnswerValue, Exercise, LearningAtoms, LessonUnit } from './lessonTypes';
import { deriveUnits } from './lessonUnits';
import { useStore } from './store';

export type LessonPhase = 'answering' | 'feedback';

/** One answered exercise, kept so revisiting a unit restores the answer. */
export interface ExerciseResult {
  answer: AnswerValue;
  correct: boolean;
}

interface LessonSlice {
  /** Topic the loaded lesson belongs to (guards against stale async loads). */
  topicId: string | null;
  loading: boolean;
  atoms: LearningAtoms | null;
  units: LessonUnit[];
  lessonCompleted: boolean;
  /** The unit open in the panel; null while loading or when there is no lesson. */
  currentUnitId: string | null;
  exerciseIndex: number;
  /**
   * Submitted answers keyed by STABLE exercise id (loaded from the server +
   * updated live). Unit and lesson completion are DERIVED from this: a unit is
   * done when all its exercises are answered, so editing other atoms never
   * resets progress. The current exercise's phase is derived from it too.
   */
  results: Record<string, ExerciseResult>;

  // --- Mistakes unit: a requeue-until-correct loop over the wrong answers. ---
  mistakesQueue: string[];
  mistakesPhase: LessonPhase;
  mistakesLastCorrect: boolean;
  mistakesLastAnswer: AnswerValue | null;

  /** Exercise/atom lookups derived from the atoms file. */
  exerciseById: Record<string, Exercise>;
  atomIdByExerciseId: Record<string, string>;
  /** Last in-flight answer save, awaited before a recompute so it sees it. */
  pendingSave: Promise<void> | null;

  loadLesson: (topicId: string) => Promise<void>;
  submitAnswer: (answer: AnswerValue) => void;
  continueNext: () => Promise<void>;
  /** Moves to the previous exercise (across units); a no-op at the first one. */
  goBack: () => void;
  goToUnit: (unitId: string) => void;
  submitMistake: (answer: AnswerValue) => void;
  continueMistake: () => void;
  /** Called by the boss unit after a passing grade; recomputes completion. */
  bossUnitPassed: () => Promise<void>;
  reset: () => void;
}

/**
 * A discovery/practice unit is done when all its exercises are answered; a boss
 * unit when its question was passed; the mistakes unit when the wrong pool is
 * empty. All derived from stable data — no server-stored unit rows.
 */
export function isUnitDone(
  unit: LessonUnit,
  results: Record<string, ExerciseResult>,
  passedBossIds: Record<string, boolean>,
  mistakesDone: boolean,
): boolean {
  if (unit.kind === 'mistakes') return mistakesDone;
  if (unit.kind === 'boss') return !!passedBossIds[unit.id.slice(2)];
  return unit.exerciseIds.length > 0 && unit.exerciseIds.every((id) => results[id] != null);
}

/** Exercises whose latest recorded answer is wrong — the mistakes-loop pool. */
export function wrongExerciseIds(
  units: LessonUnit[],
  results: Record<string, ExerciseResult>,
): string[] {
  const out: string[] = [];
  const seen = new Set<string>();
  for (const u of units) {
    if (u.kind !== 'discovery' && u.kind !== 'practice' && u.kind !== 'capstone') continue;
    for (const id of u.exerciseIds) {
      if (results[id]?.correct === false && !seen.has(id)) {
        seen.add(id);
        out.push(id);
      }
    }
  }
  return out;
}

/** True when there is nothing left in the mistakes pool. */
export function mistakesCleared(
  units: LessonUnit[],
  results: Record<string, ExerciseResult>,
): boolean {
  return wrongExerciseIds(units, results).length === 0;
}

/**
 * Derived lesson completion (same rule as the server): every discovery/practice
 * exercise answered AND every boss question passed. Derived so a stale stored
 * flag never shows "completed" after new exercises are added.
 */
export function lessonComplete(
  units: LessonUnit[],
  results: Record<string, ExerciseResult>,
  passedBossIds: Record<string, boolean>,
): boolean {
  const required = units
    .filter((u) => u.kind === 'discovery' || u.kind === 'practice' || u.kind === 'capstone')
    .flatMap((u) => u.exerciseIds);
  if (required.length === 0 || !required.every((id) => results[id] != null)) return false;
  return units.filter((u) => u.kind === 'boss').every((u) => !!passedBossIds[u.id.slice(2)]);
}

/** True when any exercise of a (non-boss) unit was answered incorrectly. */
export function unitHasMistake(unit: LessonUnit, results: Record<string, ExerciseResult>): boolean {
  if (unit.kind === 'boss' || unit.kind === 'mistakes') return false;
  return unit.exerciseIds.some((id) => results[id]?.correct === false);
}

/**
 * The unit/exerciseIndex `goBack` would land on, or null at the very first
 * exercise of the lesson. Skips units with no indexed exercises (mistakes,
 * boss) since they aren't part of this back/forward sequence.
 */
export function previousPosition(
  units: LessonUnit[],
  currentUnitId: string | null,
  exerciseIndex: number,
): { unitId: string; exerciseIndex: number } | null {
  const unit = units.find((u) => u.id === currentUnitId);
  if (!unit) return null;
  if (exerciseIndex > 0) return { unitId: unit.id, exerciseIndex: exerciseIndex - 1 };

  let prevIndex = units.findIndex((u) => u.id === unit.id) - 1;
  while (prevIndex >= 0 && units[prevIndex].exerciseIds.length === 0) prevIndex--;
  if (prevIndex < 0) return null;
  const prev = units[prevIndex];
  return { unitId: prev.id, exerciseIndex: prev.exerciseIds.length - 1 };
}

/** Index of the first not-yet-done unit (the unlock frontier); units.length when all done. */
export function frontierIndex(
  units: LessonUnit[],
  results: Record<string, ExerciseResult>,
  passedBossIds: Record<string, boolean>,
  mistakesDone: boolean,
): number {
  for (let i = 0; i < units.length; i++) {
    if (!isUnitDone(units[i], results, passedBossIds, mistakesDone)) return i;
  }
  return units.length;
}

function passedBossIds(): Record<string, boolean> {
  const results = useStore.getState().bossFightResults;
  const out: Record<string, boolean> = {};
  for (const [qid, r] of Object.entries(results)) {
    if (r.passed) out[qid] = true;
  }
  return out;
}

const EMPTY: Pick<
  LessonSlice,
  | 'atoms' | 'units' | 'lessonCompleted' | 'currentUnitId' | 'exerciseIndex'
  | 'results' | 'mistakesQueue' | 'mistakesPhase' | 'mistakesLastCorrect'
  | 'mistakesLastAnswer' | 'exerciseById' | 'atomIdByExerciseId' | 'pendingSave'
> = {
  atoms: null,
  units: [],
  lessonCompleted: false,
  currentUnitId: null,
  exerciseIndex: 0,
  results: {},
  mistakesQueue: [],
  mistakesPhase: 'answering',
  mistakesLastCorrect: false,
  mistakesLastAnswer: null,
  exerciseById: {},
  atomIdByExerciseId: {},
  pendingSave: null,
};

export const useLesson = create<LessonSlice>((set, get) => ({
  topicId: null,
  loading: false,
  ...EMPTY,

  async loadLesson(topicId) {
    set({ topicId, loading: true, ...EMPTY });
    try {
      const [atoms, state] = await Promise.all([fetchAtoms(topicId), fetchLessonState(topicId)]);
      if (get().topicId !== topicId) return; // another topic selected meanwhile
      if (!atoms) {
        set({ loading: false });
        return;
      }
      const bossFight = useStore.getState().topic?.bossFight ?? [];
      const units = deriveUnits(atoms, bossFight);

      const results: Record<string, ExerciseResult> = {};
      for (const a of state?.answers ?? []) {
        try {
          results[a.exerciseId] = { answer: JSON.parse(a.answerJson), correct: a.correct };
        } catch {
          /* skip an unparseable saved answer */
        }
      }
      // Answers given offline are not in the server state yet (and the state
      // itself may be a cached copy), so replay the outbox over it — later
      // entries win, which is the same order the server will apply them in.
      for (const queued of await pendingAnswers(topicId)) {
        if (!queued) continue;
        try {
          results[queued.exerciseId] = { answer: JSON.parse(queued.answerJson), correct: queued.correct };
        } catch {
          /* ignore a corrupt queue entry */
        }
      }

      const exerciseById: Record<string, Exercise> = {};
      const atomIdByExerciseId: Record<string, string> = {};
      for (const atom of atoms.atoms) {
        for (const ex of [...(atom.discovery ?? []), ...(atom.practice ?? [])]) {
          exerciseById[ex.id] = ex;
          atomIdByExerciseId[ex.id] = atom.id;
        }
      }

      const cleared = mistakesCleared(units, results);
      const frontier = frontierIndex(units, results, passedBossIds(), cleared);
      const currentUnitId = units.length === 0 ? null : units[Math.min(frontier, units.length - 1)].id;
      const mistakesQueue =
        currentUnitId === 'mistakes' ? shuffled(wrongExerciseIds(units, results)) : [];
      set({
        loading: false,
        atoms,
        units,
        lessonCompleted: lessonComplete(units, results, passedBossIds()),
        currentUnitId,
        results,
        mistakesQueue,
        exerciseById,
        atomIdByExerciseId,
      });
    } catch {
      if (get().topicId === topicId) set({ loading: false });
    }
  },

  submitAnswer(answer) {
    const s = get();
    const unit = s.units.find((u) => u.id === s.currentUnitId);
    const exerciseId = unit?.exerciseIds[s.exerciseIndex];
    const exercise = exerciseId ? s.exerciseById[exerciseId] : undefined;
    if (!s.topicId || !s.atoms || !unit || !exercise) return;

    const correct = grade(exercise, answer, useLang.getState().lang);
    // Recording the result flips this exercise into the feedback phase (phase
    // is derived from results in the panel) and keeps the answer for revisits.
    const pendingSave = saveExerciseAnswer(s.topicId, {
      exerciseId: exercise.id,
      atomId: s.atomIdByExerciseId[exercise.id] ?? '',
      unitId: unit.id,
      context: 'lesson',
      answer,
      correct,
    });
    set({ results: { ...s.results, [exercise.id]: { answer, correct } }, pendingSave });
  },

  async continueNext() {
    const s = get();
    const unit = s.units.find((u) => u.id === s.currentUnitId);
    if (!s.topicId || !s.atoms || !unit) return;

    if (s.exerciseIndex + 1 < unit.exerciseIds.length) {
      set({ exerciseIndex: s.exerciseIndex + 1 });
      return;
    }

    // Unit finished: advance to the next unlocked unit and recompute lesson
    // completion (all exercises answered + all boss passed) on the server.
    const cleared = mistakesCleared(s.units, s.results);
    let nextIndex = s.units.findIndex((u) => u.id === unit.id) + 1;
    while (s.units[nextIndex]?.kind === 'mistakes' && cleared) nextIndex++;
    const next = s.units[nextIndex] ?? null;
    const enteringMistakes = next?.kind === 'mistakes';
    set({
      currentUnitId: next ? next.id : unit.id,
      exerciseIndex: 0,
      mistakesQueue: enteringMistakes ? shuffled(wrongExerciseIds(s.units, s.results)) : [],
      mistakesPhase: 'answering',
      mistakesLastAnswer: null,
    });

    try {
      await s.pendingSave; // make sure the last answer is persisted before recompute
      const res = await recomputeLesson(s.topicId);
      set({ lessonCompleted: res.lessonCompleted || get().lessonCompleted });
      if (res.lessonCompleted && !s.lessonCompleted) {
        // Lesson completion implies the boss-driven topic completion.
        useStore.getState().markTopicCompleted();
      }
    } catch {
      // Offline: the same rule the server applies, over the answers we hold.
      // The server recomputes from the replayed answers once it hears them.
      const local = lessonComplete(get().units, get().results, passedBossIds());
      if (local) set({ lessonCompleted: true });
    }
  },

  goBack() {
    const s = get();
    const target = previousPosition(s.units, s.currentUnitId, s.exerciseIndex);
    if (!target) return;
    set({ currentUnitId: target.unitId, exerciseIndex: target.exerciseIndex });
  },

  goToUnit(unitId) {
    const s = get();
    const index = s.units.findIndex((u) => u.id === unitId);
    if (index < 0) return;
    const cleared = mistakesCleared(s.units, s.results);
    const frontier = frontierIndex(s.units, s.results, passedBossIds(), cleared);
    if (index > frontier) return; // locked
    set({
      currentUnitId: unitId,
      exerciseIndex: 0,
      mistakesQueue: unitId === 'mistakes' ? shuffled(wrongExerciseIds(s.units, s.results)) : [],
      mistakesPhase: 'answering',
      mistakesLastAnswer: null,
    });
  },

  submitMistake(answer) {
    const s = get();
    const exId = s.mistakesQueue[0];
    const exercise = exId ? s.exerciseById[exId] : undefined;
    if (!s.topicId || !s.atoms || !exercise) return;

    const correct = grade(exercise, answer, useLang.getState().lang);
    // Re-answering upserts the same lesson row: a correct answer flips it to
    // correct (dropping it from the pool and turning the source unit's ✗ → ✓).
    const pendingSave = saveExerciseAnswer(s.topicId, {
      exerciseId: exercise.id,
      atomId: s.atomIdByExerciseId[exercise.id] ?? '',
      unitId: 'mistakes',
      context: 'lesson',
      answer,
      correct,
    });
    set({
      mistakesPhase: 'feedback',
      mistakesLastCorrect: correct,
      mistakesLastAnswer: answer,
      results: { ...s.results, [exercise.id]: { answer, correct } },
      pendingSave,
    });
  },

  continueMistake() {
    const s = get();
    const exId = s.mistakesQueue[0];
    let queue = exId && s.results[exId]?.correct === false
      ? [...s.mistakesQueue.slice(1), exId]
      : s.mistakesQueue.slice(1);
    queue = queue.filter((id) => s.results[id]?.correct === false);

    if (queue.length === 0) {
      const idx = s.units.findIndex((u) => u.id === 'mistakes');
      const next = s.units[idx + 1] ?? null;
      set({
        mistakesQueue: [],
        currentUnitId: next ? next.id : s.currentUnitId,
        exerciseIndex: 0,
        mistakesPhase: 'answering',
        mistakesLastAnswer: null,
      });
    } else {
      set({ mistakesQueue: queue, mistakesPhase: 'answering', mistakesLastAnswer: null });
    }
  },

  async bossUnitPassed() {
    const s = get();
    if (!s.topicId) return;
    try {
      const res = await recomputeLesson(s.topicId);
      set({ lessonCompleted: res.lessonCompleted || get().lessonCompleted });
      if (res.lessonCompleted && !s.lessonCompleted) {
        useStore.getState().markTopicCompleted();
      }
    } catch {
      /* best-effort; the pass itself is already stored in boss_fight_answer */
    }
  },

  reset() {
    set({ topicId: null, loading: false, ...EMPTY });
  },
}));
