import { create } from 'zustand';
import { useLang } from '../i18n';
import {
  fetchReviewList,
  fetchReviewSummary,
  fetchReviewTopics,
  markReviewAnswer,
  restartReviewAll,
  restartReviewTopic,
  saveExerciseAnswer,
  setReviewTopicEnabled,
} from './api';
import { grade } from './grading';
import type { AnswerValue, ReviewItem, ReviewSummary, ReviewTopic } from './lessonTypes';

export type ReviewPhase = 'answering' | 'feedback';

/**
 * Global review over the practice exercises of fully completed lessons. Whether
 * an exercise is in the list is persistent per-exercise state on the server
 * (review_pool.pending). This store holds a client-side queue over the current
 * list: the head is the shown item, a correct answer removes it, a wrong answer
 * moves it to the tail so it resurfaces. Toggling a topic or a "start again"
 * mutates the server state, then reloads the list.
 */
interface ReviewSlice {
  summary: ReviewSummary | null;
  /** Topics with pooled exercises (for the review tree), with pending/total/enabled. */
  topics: ReviewTopic[];
  /** The remaining items to review; the head is the current question. */
  queue: ReviewItem[];
  loading: boolean;
  error: string | null;
  phase: ReviewPhase;
  lastCorrect: boolean;
  lastAnswer: AnswerValue | null;
  /** Answers submitted in this run (for the "answered / left" header). */
  answered: number;

  loadSummary: () => Promise<void>;
  /** Loads the list + topics and shuffles the list into the queue. */
  start: () => Promise<void>;
  submit: (answer: AnswerValue) => Promise<void>;
  next: () => void;
  /** Persists a topic's enabled flag, then reloads the list (keeping the current item). */
  toggleTopic: (topicId: string, enabled: boolean) => Promise<void>;
  /** Per-topic "start again": returns the topic's answered exercises to the list. */
  restartTopic: (topicId: string) => Promise<void>;
  /** Global "start again": returns every answered exercise to the list. */
  restartAll: () => Promise<void>;
  /** Refetches the list + topics, optionally keeping the current item at the head. */
  reload: (preserveCurrent: boolean) => Promise<void>;
  reset: () => void;
}

/** The item the cursor points at (the queue head), or null when the list is empty. */
export function currentReviewItem(queue: ReviewItem[]): ReviewItem | null {
  return queue[0] ?? null;
}

function sameItem(a: ReviewItem, b: ReviewItem): boolean {
  return a.topicId === b.topicId && a.exercise.id === b.exercise.id;
}

/** Fisher-Yates shuffle (returns a new array). */
function shuffled<T>(arr: T[]): T[] {
  const out = [...arr];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

/**
 * Fetches the list + topics and builds the queue. When {@link preserve} is the
 * current item and it is still in the list, it stays at the head and the rest
 * are shuffled after it — so toggling an unrelated topic doesn't swap the shown
 * question.
 */
async function rebuild(
  preserve: ReviewItem | null,
): Promise<{ queue: ReviewItem[]; topics: ReviewTopic[] }> {
  const [items, topics] = await Promise.all([fetchReviewList(), fetchReviewTopics()]);
  const keep = preserve && items.some((i) => sameItem(i, preserve)) ? preserve : null;
  const rest = shuffled(keep ? items.filter((i) => !sameItem(i, keep)) : items);
  return { queue: keep ? [keep, ...rest] : rest, topics };
}

export const useReview = create<ReviewSlice>((set, get) => ({
  summary: null,
  topics: [],
  queue: [],
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
      const { queue, topics } = await rebuild(null);
      set({ queue, topics, loading: false });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  async submit(answer) {
    const s = get();
    const item = currentReviewItem(s.queue);
    if (!item) return;

    const correct = grade(item.exercise, answer, useLang.getState().lang);

    // Persist: markReviewAnswer drops it from the list on a correct answer;
    // saveExerciseAnswer keeps the per-exercise answer log (context 'review').
    void markReviewAnswer(item.topicId, item.exercise.id, correct);
    void saveExerciseAnswer(item.topicId, {
      exerciseId: item.exercise.id,
      atomId: item.atomId,
      unitId: '',
      context: 'review',
      answer,
      correct,
    });
    set({ phase: 'feedback', lastCorrect: correct, lastAnswer: answer, answered: s.answered + 1 });
  },

  next() {
    const s = get();
    const [head, ...tail] = s.queue;
    if (head === undefined) {
      set({ phase: 'answering', lastAnswer: null });
      return;
    }
    // Correct → the item leaves the list (and its topic's pending count drops);
    // wrong → it goes to the tail to be retried later this run.
    const queue = s.lastCorrect ? tail : [...tail, head];
    const topics = s.lastCorrect
      ? s.topics.map((t) =>
          t.topicId === head.topicId ? { ...t, pending: Math.max(0, t.pending - 1) } : t,
        )
      : s.topics;
    set({ queue, topics, phase: 'answering', lastAnswer: null });
  },

  async toggleTopic(topicId, enabled) {
    const prev = get().topics;
    // Optimistic flip so the checkbox responds instantly.
    set({ topics: prev.map((t) => (t.topicId === topicId ? { ...t, enabled } : t)) });
    try {
      await setReviewTopicEnabled(topicId, enabled);
    } catch (e) {
      set({ topics: prev, error: (e as Error).message });
      return;
    }
    await get().reload(true);
  },

  async restartTopic(topicId) {
    try {
      await restartReviewTopic(topicId);
    } catch (e) {
      set({ error: (e as Error).message });
      return;
    }
    await get().reload(true);
  },

  async restartAll() {
    try {
      await restartReviewAll();
    } catch (e) {
      set({ error: (e as Error).message });
      return;
    }
    set({ answered: 0 });
    await get().reload(false);
  },

  async reload(preserveCurrent) {
    try {
      const preserve = preserveCurrent ? currentReviewItem(get().queue) : null;
      const { queue, topics } = await rebuild(preserve);
      set({ queue, topics, phase: 'answering', lastAnswer: null });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  reset() {
    set({ queue: [], error: null, phase: 'answering', lastAnswer: null, answered: 0 });
  },
}));
