import { create } from 'zustand';
import { useLang } from '../i18n';
import {
  completeUnit as apiCompleteUnit,
  fetchAtoms,
  fetchLessonState,
  saveExerciseAnswer,
} from './api';
import { grade } from './grading';
import type { AnswerValue, AtomsResponse, Exercise, LessonUnit } from './lessonTypes';
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
  atoms: AtomsResponse | null;
  units: LessonUnit[];
  /** Unit ids completed for the current atoms hash (server-confirmed). */
  completedUnits: Record<string, boolean>;
  lessonCompleted: boolean;
  /** The unit open in the panel; null while loading or when there is no lesson. */
  currentUnitId: string | null;
  exerciseIndex: number;
  /**
   * Submitted answers keyed by exercise id (loaded from the server + updated
   * live). The current exercise's phase is derived from this: a stored result
   * means "feedback" (show the saved answer), otherwise "answering".
   */
  results: Record<string, ExerciseResult>;
  /** Set when the server said the atoms file changed under us (409). */
  stale: boolean;

  /** Exercise/atom lookups derived from the atoms file. */
  exerciseById: Record<string, Exercise>;
  atomIdByExerciseId: Record<string, string>;

  loadLesson: (topicId: string) => Promise<void>;
  submitAnswer: (answer: AnswerValue) => void;
  continueNext: () => Promise<void>;
  goToUnit: (unitId: string) => void;
  /** Called by the boss unit after a passing grade; persists + advances. */
  bossUnitPassed: (unitId: string) => Promise<void>;
  reset: () => void;
}

/** A boss unit also counts as done when its question was passed in the old dialog. */
export function isUnitDone(
  unit: LessonUnit,
  completedUnits: Record<string, boolean>,
  passedBossIds: Record<string, boolean>,
): boolean {
  if (completedUnits[unit.id]) return true;
  if (unit.kind === 'boss') {
    return !!passedBossIds[unit.id.slice(2)];
  }
  return false;
}

/** True when any exercise of a (non-boss) unit was answered incorrectly. */
export function unitHasMistake(unit: LessonUnit, results: Record<string, ExerciseResult>): boolean {
  if (unit.kind === 'boss') return false;
  return unit.exerciseIds.some((id) => results[id]?.correct === false);
}

/** Index of the first not-yet-done unit (the unlock frontier); units.length when all done. */
export function frontierIndex(
  units: LessonUnit[],
  completedUnits: Record<string, boolean>,
  passedBossIds: Record<string, boolean>,
): number {
  for (let i = 0; i < units.length; i++) {
    if (!isUnitDone(units[i], completedUnits, passedBossIds)) return i;
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
  | 'atoms' | 'units' | 'completedUnits' | 'lessonCompleted' | 'currentUnitId'
  | 'exerciseIndex' | 'results' | 'stale' | 'exerciseById' | 'atomIdByExerciseId'
> = {
  atoms: null,
  units: [],
  completedUnits: {},
  lessonCompleted: false,
  currentUnitId: null,
  exerciseIndex: 0,
  results: {},
  stale: false,
  exerciseById: {},
  atomIdByExerciseId: {},
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
      const units = deriveUnits(atoms.atoms, bossFight);
      const completedUnits: Record<string, boolean> = {};
      for (const id of state?.completedUnits ?? []) completedUnits[id] = true;

      const results: Record<string, ExerciseResult> = {};
      for (const a of state?.answers ?? []) {
        try {
          results[a.exerciseId] = { answer: JSON.parse(a.answerJson), correct: a.correct };
        } catch {
          /* skip an unparseable saved answer */
        }
      }

      const exerciseById: Record<string, Exercise> = {};
      const atomIdByExerciseId: Record<string, string> = {};
      for (const atom of atoms.atoms.atoms) {
        for (const ex of [...(atom.discovery ?? []), ...(atom.practice ?? [])]) {
          exerciseById[ex.id] = ex;
          atomIdByExerciseId[ex.id] = atom.id;
        }
      }

      const frontier = frontierIndex(units, completedUnits, passedBossIds());
      set({
        loading: false,
        atoms,
        units,
        completedUnits,
        lessonCompleted: state?.lessonCompleted ?? false,
        currentUnitId: units.length === 0 ? null : units[Math.min(frontier, units.length - 1)].id,
        results,
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
    set({ results: { ...s.results, [exercise.id]: { answer, correct } } });
    void saveExerciseAnswer(s.topicId, {
      exerciseId: exercise.id,
      atomId: s.atomIdByExerciseId[exercise.id] ?? '',
      unitId: unit.id,
      context: 'lesson',
      atomsHash: s.atoms.atomsHash,
      answer,
      correct,
    });
  },

  async continueNext() {
    const s = get();
    const unit = s.units.find((u) => u.id === s.currentUnitId);
    if (!s.topicId || !s.atoms || !unit) return;

    if (s.exerciseIndex + 1 < unit.exerciseIds.length) {
      set({ exerciseIndex: s.exerciseIndex + 1 });
      return;
    }

    // Unit finished: persist (idempotent) and advance to the next unlocked unit.
    try {
      const res = await apiCompleteUnit(s.topicId, unit.id, s.atoms.atomsHash);
      if (res.stale) {
        set({ stale: true });
        return;
      }
      const completedUnits = { ...get().completedUnits, [unit.id]: true };
      const index = s.units.findIndex((u) => u.id === unit.id);
      const next = s.units[index + 1] ?? null;
      set({
        completedUnits,
        lessonCompleted: res.lessonCompleted || get().lessonCompleted,
        currentUnitId: next ? next.id : unit.id,
        exerciseIndex: 0,
      });
      if (res.lessonCompleted && !s.lessonCompleted) {
        // Same celebration as the boss dialog; lesson completion implies the
        // boss-driven topic completion, so the flags agree.
        useStore.getState().markTopicCompleted();
      }
    } catch {
      /* leave the unit open; the learner can hit Continue again */
    }
  },

  goToUnit(unitId) {
    const s = get();
    const index = s.units.findIndex((u) => u.id === unitId);
    if (index < 0) return;
    const frontier = frontierIndex(s.units, s.completedUnits, passedBossIds());
    if (index > frontier) return; // locked
    set({ currentUnitId: unitId, exerciseIndex: 0 });
  },

  async bossUnitPassed(unitId) {
    const s = get();
    if (!s.topicId || !s.atoms) return;
    try {
      const res = await apiCompleteUnit(s.topicId, unitId, s.atoms.atomsHash);
      if (res.stale) {
        set({ stale: true });
        return;
      }
      set({
        completedUnits: { ...get().completedUnits, [unitId]: true },
        lessonCompleted: res.lessonCompleted || get().lessonCompleted,
      });
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
