import { create } from 'zustand';
import { useLang } from '../i18n';
import {
  abandonReviewSession,
  answerReview,
  fetchActiveReviewSession,
  fetchReviewSummary,
  saveExerciseAnswer,
  startReviewSession,
} from './api';
import { grade } from './grading';
import type { AnswerValue, ReviewItem, ReviewSession, ReviewSummary } from './lessonTypes';

export type ReviewPhase = 'answering' | 'feedback';

interface ReviewSlice {
  summary: ReviewSummary | null;
  session: ReviewSession | null;
  loading: boolean;
  error: string | null;
  phase: ReviewPhase;
  lastCorrect: boolean;
  lastAnswer: AnswerValue | null;
  /** Answers submitted in this session (for the "answered / total" header). */
  answered: number;

  loadSummary: () => Promise<void>;
  /** Resumes the active session or starts a new one over the current pool. */
  start: () => Promise<void>;
  submit: (answer: AnswerValue) => Promise<void>;
  next: () => void;
  abandon: () => Promise<void>;
  reset: () => void;
}

/** The item the cursor points at, or null when the session is finished/absent. */
export function currentReviewItem(session: ReviewSession | null): ReviewItem | null {
  if (!session || session.finished) return null;
  const index = session.queue[session.position];
  return index === undefined ? null : session.items[index] ?? null;
}

export const useReview = create<ReviewSlice>((set, get) => ({
  summary: null,
  session: null,
  loading: false,
  error: null,
  phase: 'answering',
  lastCorrect: false,
  lastAnswer: null,
  answered: 0,

  async loadSummary() {
    try {
      set({ summary: await fetchReviewSummary() });
    } catch {
      /* badge is a nice-to-have */
    }
  },

  async start() {
    set({ loading: true, error: null, phase: 'answering', lastAnswer: null, answered: 0 });
    try {
      const active = await fetchActiveReviewSession();
      const session = active ?? (await startReviewSession());
      // Resuming mid-session: everything before the cursor was already answered.
      set({ session, loading: false, answered: session.position });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  async submit(answer) {
    const s = get();
    const item = currentReviewItem(s.session);
    if (!s.session || !item) return;

    const correct = grade(item.exercise, answer, useLang.getState().lang);
    set({ phase: 'feedback', lastCorrect: correct, lastAnswer: answer });

    void saveExerciseAnswer(item.topicId, {
      exerciseId: item.exercise.id,
      atomId: item.atomId,
      unitId: '',
      context: 'review',
      answer,
      correct,
    });
    try {
      const itemIndex = s.session.queue[s.session.position];
      const res = await answerReview(s.session.sessionId, { itemIndex, correct, answer });
      set({
        session: { ...s.session, queue: res.queue, position: res.position, finished: res.finished },
        answered: get().answered + 1,
      });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  next() {
    set({ phase: 'answering', lastAnswer: null });
  },

  async abandon() {
    const s = get();
    if (s.session && !s.session.finished) {
      try {
        await abandonReviewSession(s.session.sessionId);
      } catch {
        /* ignore */
      }
    }
    set({ session: null, phase: 'answering', lastAnswer: null, answered: 0 });
  },

  reset() {
    set({ session: null, error: null, phase: 'answering', lastAnswer: null, answered: 0 });
  },
}));
