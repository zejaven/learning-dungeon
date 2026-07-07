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

/** The server-computed cursor move, applied on Continue rather than on answer. */
type Advance = { queue: number[]; position: number; finished: boolean };

interface ReviewSlice {
  summary: ReviewSummary | null;
  session: ReviewSession | null;
  loading: boolean;
  error: string | null;
  phase: ReviewPhase;
  lastCorrect: boolean;
  lastAnswer: AnswerValue | null;
  /**
   * The server's next cursor position for the just-answered item. It is held
   * here during feedback and applied only on Continue, so the session cursor
   * keeps pointing at the answered item while its feedback is shown. Advancing
   * on answer instead desynced the cursor from the phase and made the next
   * question render as if already answered.
   */
  pendingAdvance: Promise<Advance> | null;
  /** Answers submitted in this session (for the "answered / total" header). */
  answered: number;

  loadSummary: () => Promise<void>;
  /** Resumes the active session or starts a new one over the current pool. */
  start: () => Promise<void>;
  submit: (answer: AnswerValue) => Promise<void>;
  next: () => Promise<void>;
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
  pendingAdvance: null,
  answered: 0,

  async loadSummary() {
    try {
      set({ summary: await fetchReviewSummary() });
    } catch {
      /* badge is a nice-to-have */
    }
  },

  async start() {
    set({
      loading: true,
      error: null,
      phase: 'answering',
      lastAnswer: null,
      pendingAdvance: null,
      answered: 0,
    });
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

    void saveExerciseAnswer(item.topicId, {
      exerciseId: item.exercise.id,
      atomId: item.atomId,
      unitId: '',
      context: 'review',
      answer,
      correct,
    });
    // Record the answer and start the server advance, but do NOT move the
    // session cursor yet — it stays on this item so its feedback renders here.
    const itemIndex = s.session.queue[s.session.position];
    const pendingAdvance = answerReview(s.session.sessionId, { itemIndex, correct, answer });
    void pendingAdvance.catch(() => {}); // handled on Continue; avoid unhandled rejection
    set({
      phase: 'feedback',
      lastCorrect: correct,
      lastAnswer: answer,
      pendingAdvance,
      answered: s.answered + 1,
    });
  },

  async next() {
    const pending = get().pendingAdvance;
    if (!pending) {
      set({ phase: 'answering', lastAnswer: null });
      return;
    }
    // Apply the cursor move and flip to answering together, so the next
    // question never renders under the previous item's feedback.
    try {
      const res = await pending;
      const session = get().session;
      set({
        session: session ? { ...session, queue: res.queue, position: res.position, finished: res.finished } : session,
        phase: 'answering',
        lastAnswer: null,
        pendingAdvance: null,
      });
    } catch (e) {
      set({ phase: 'answering', lastAnswer: null, pendingAdvance: null, error: (e as Error).message });
    }
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
    set({ session: null, phase: 'answering', lastAnswer: null, pendingAdvance: null, answered: 0 });
  },

  reset() {
    set({ session: null, error: null, phase: 'answering', lastAnswer: null, pendingAdvance: null, answered: 0 });
  },
}));
