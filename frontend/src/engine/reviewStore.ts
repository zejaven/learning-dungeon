import { useMemo } from 'react';
import { create } from 'zustand';
import { domainOf } from '../domains';
import { useLang } from '../i18n';
import { useDomain } from './domainStore';
import { useStore } from './store';
import {
  fetchReviewList,
  fetchReviewTopics,
  markReviewAnswer,
  restartReviewAll,
  restartReviewTopic,
  saveExerciseAnswer,
  setReviewTopicEnabled,
} from './api';
import { grade } from './grading';
import type { AnswerValue, ReviewItem, ReviewTopic } from './lessonTypes';
import type { TopicSummary } from './traceTypes';

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
  /**
   * Every topic with pooled exercises, UNFILTERED by domain — the source of the
   * home-screen badge, which counts only the active domain (see
   * {@link useReviewBadge}). Kept raw so switching domains re-counts without a
   * refetch.
   */
  pool: ReviewTopic[];
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

  /** Loads the pooled topics of every domain (for the badge). */
  loadPool: () => Promise<void>;
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
): Promise<{ queue: ReviewItem[]; topics: ReviewTopic[]; pool: ReviewTopic[] }> {
  const [allItems, allTopics] = await Promise.all([fetchReviewList(), fetchReviewTopics()]);
  const inDomain = activeDomainFilter();
  const items = allItems.filter((i) => inDomain(i.topicId));
  const topics = allTopics.filter((t) => inDomain(t.topicId));
  const keep = preserve && items.some((i) => sameItem(i, preserve)) ? preserve : null;
  const rest = shuffled(keep ? items.filter((i) => !sameItem(i, keep)) : items);
  return { queue: keep ? [keep, ...rest] : rest, topics, pool: allTopics };
}

/**
 * Review is scoped to a subject area: only exercises of topics in {@link domainId}
 * take part. Topics missing from the loaded summaries count as 'java' (same
 * default as {@link domainOf}); before summaries load nothing is filtered out,
 * which only ever shows extra items briefly.
 */
function domainFilter(summaries: TopicSummary[], domainId: string): (topicId: string) => boolean {
  if (summaries.length === 0) return () => true;
  const byId = new Map(summaries.map((t) => [t.id, t]));
  return (topicId) => domainOf(byId.get(topicId) ?? {}) === domainId;
}

/** {@link domainFilter} for the domain the app currently shows. */
function activeDomainFilter(): (topicId: string) => boolean {
  return domainFilter(useStore.getState().topics, useDomain.getState().domainId);
}

/**
 * How many exercises the active domain has waiting for review — the header
 * badge. Counts pending exercises of enabled topics only, which is exactly what
 * a review run would ask. Recomputes when the domain switches, so the badge
 * follows the domain pills without a refetch; 0 until topic summaries load,
 * since without them the domain of a pooled topic is unknown.
 */
export function useReviewBadge(): number {
  const pool = useReview((s) => s.pool);
  const summaries = useStore((s) => s.topics);
  const domainId = useDomain((s) => s.domainId);
  return useMemo(() => {
    if (summaries.length === 0) return 0;
    const inDomain = domainFilter(summaries, domainId);
    return pool.reduce((n, t) => (t.enabled && inDomain(t.topicId) ? n + t.pending : n), 0);
  }, [pool, summaries, domainId]);
}

export const useReview = create<ReviewSlice>((set, get) => ({
  pool: [],
  topics: [],
  queue: [],
  loading: false,
  error: null,
  phase: 'answering',
  lastCorrect: false,
  lastAnswer: null,
  answered: 0,

  async loadPool() {
    try {
      set({ pool: await fetchReviewTopics() });
    } catch {
      /* badge is a nice-to-have */
    }
  },

  async start() {
    set({ loading: true, error: null, phase: 'answering', lastAnswer: null, answered: 0 });
    try {
      const { queue, topics, pool } = await rebuild(null);
      set({ queue, topics, pool, loading: false });
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
    const drop = (list: ReviewTopic[]) =>
      list.map((t) =>
        t.topicId === head.topicId ? { ...t, pending: Math.max(0, t.pending - 1) } : t,
      );
    const topics = s.lastCorrect ? drop(s.topics) : s.topics;
    const pool = s.lastCorrect ? drop(s.pool) : s.pool;
    set({ queue, topics, pool, phase: 'answering', lastAnswer: null });
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
      const { queue, topics, pool } = await rebuild(preserve);
      set({ queue, topics, pool, phase: 'answering', lastAnswer: null });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  reset() {
    set({ queue: [], error: null, phase: 'answering', lastAnswer: null, answered: 0 });
  },
}));
