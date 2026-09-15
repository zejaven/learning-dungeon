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
  saveExerciseAnswer,
  setReviewTopicEnabled,
} from './api';
import { grade } from './grading';
import type { AnswerValue, ReviewDue, ReviewItem, ReviewTopic } from './lessonTypes';
import type { TopicSummary } from './traceTypes';

export type ReviewPhase = 'answering' | 'feedback';

/**
 * 'due' walks what the repetition schedule says is due and records the answers.
 * 'ahead' is a deliberate extra drill over one topic's not-yet-due and already
 * mastered exercises; it logs answers as history but does NOT touch the
 * schedule, which is what makes it a safe replacement for the old "return this
 * topic's answered exercises" button.
 */
export type ReviewMode = 'due' | 'ahead';

/** Where one pooled exercise stands right now (mirrors ReviewSchedule.State). */
export type ReviewItemState = 'due' | 'lapsed' | 'waiting' | 'mastered';

/**
 * Most exercises one run offers before it stops and offers the next batch. A
 * cap, not a limit: the backlog is always clearable by continuing, which matters
 * because anything left unanswered keeps ticking towards its lapse.
 */
export const SESSION_SIZE = 30;

/** How often the derived due/waiting counts are recomputed while the app is open. */
const CLOCK_TICK_MS = 60_000;

/**
 * Global review over the practice exercises of fully completed lessons, on a
 * spaced-repetition schedule.
 *
 * The server hands over every pooled exercise with its due and expiry instants
 * and never says what is "due now" — that is derived here against the local
 * clock. It is what lets the response be cached for offline review, lets the
 * per-domain filter and the session cap apply after the fact, and keeps the
 * badge honest when the tab has been open all night.
 */
interface ReviewSlice {
  /**
   * Every topic with pooled exercises, UNFILTERED by domain — the source of the
   * home-screen badge, which counts only the active domain (see
   * {@link useReviewBadge}). Kept raw so switching domains re-counts without a
   * refetch.
   */
  pool: ReviewTopic[];
  /** Topics with pooled exercises (for the review tree), domain-filtered. */
  topics: ReviewTopic[];
  /** The remaining items of this run; the head is the current question. */
  queue: ReviewItem[];
  /** How many were eligible when the run was built, before {@link SESSION_SIZE}. */
  offered: number;
  mode: ReviewMode;
  /** The topic being drilled ahead of schedule, when {@link mode} is 'ahead'. */
  aheadTopicId: string | null;
  loading: boolean;
  error: string | null;
  phase: ReviewPhase;
  lastCorrect: boolean;
  lastAnswer: AnswerValue | null;
  /** Answers submitted in this run (for the "answered / left" header). */
  answered: number;
  /**
   * Items already scored in this run, keyed by {@link itemKey}. A wrong answer
   * puts the item back at the tail, and the retry must NOT be sent again: the
   * first attempt is what decides the schedule, so a correct retry a minute
   * later would otherwise undo the step reset the mistake earned.
   */
  gradedIds: Set<string>;
  /**
   * Whether the answer now showing feedback was that item's first attempt.
   * {@link gradedIds} cannot answer this by the time {@link next} runs — it was
   * updated by {@link submit} a moment earlier — and a retry must not decrement
   * the topic counts a second time.
   */
  lastWasFirst: boolean;
  /** Bumped on a tick/refocus so derived due counts re-render. */
  clock: number;

  /** Loads the pooled topics of every domain (for the badge). */
  loadPool: () => Promise<void>;
  /** Starts a fresh run over what is due in the active domain. */
  start: () => Promise<void>;
  /** Queues the next batch of a backlog, keeping what this run already answered. */
  continueRun: () => Promise<void>;
  /** Starts an ahead-of-schedule drill over one topic; does not affect the schedule. */
  startAhead: (topicId: string) => Promise<void>;
  submit: (answer: AnswerValue) => Promise<void>;
  next: () => void;
  /** Persists a topic's enabled flag, then reloads (keeping the current item). */
  toggleTopic: (topicId: string, enabled: boolean) => Promise<void>;
  /** Destructive: puts every exercise back on the first rung of the ladder. */
  resetSchedule: () => Promise<void>;
  /** Refetches, optionally keeping the current item at the head. */
  reload: (preserveCurrent: boolean) => Promise<void>;
  reset: () => void;
}

/** The item the cursor points at (the queue head), or null when the run is empty. */
export function currentReviewItem(queue: ReviewItem[]): ReviewItem | null {
  return queue[0] ?? null;
}

/** Identity of a pooled exercise across topics. */
export function itemKey(item: ReviewItem): string {
  return `${item.topicId}:${item.exercise.id}`;
}

function sameItem(a: ReviewItem, b: ReviewItem): boolean {
  return a.topicId === b.topicId && a.exercise.id === b.exercise.id;
}

// --- derived schedule state -------------------------------------------------

/** Where an exercise stands at {@link now} (epoch ms). */
export function itemState(item: ReviewItem, now: number): ReviewItemState {
  if (item.mastered) return 'mastered';
  return dueState(item.dueAt, item.expiresAt, now);
}

/**
 * A lapsed exercise is offered exactly like a due one — missing its window
 * costs the ladder, never the exercise, so it never silently disappears.
 */
export function isOffered(state: ReviewItemState): boolean {
  return state === 'due' || state === 'lapsed';
}

function dueState(dueAt: string, expiresAt: string | null, now: number): ReviewItemState {
  const due = Date.parse(dueAt);
  if (!Number.isFinite(due) || now < due) return 'waiting';
  const expires = expiresAt ? Date.parse(expiresAt) : Number.POSITIVE_INFINITY;
  return Number.isFinite(expires) && now > expires ? 'lapsed' : 'due';
}

export interface ReviewCounts {
  due: number;
  waiting: number;
  mastered: number;
  total: number;
}

/** Counts for one topic, derived from its compact schedule against {@link now}. */
export function topicCounts(topic: ReviewTopic, now: number): ReviewCounts {
  let due = 0;
  for (const entry of topic.schedule) {
    if (isOffered(dueState(entry.dueAt, entry.expiresAt, now))) due++;
  }
  return {
    due,
    waiting: topic.schedule.length - due,
    mastered: topic.mastered,
    total: topic.total,
  };
}

/** The soonest a waiting exercise of these topics comes up, or null. */
export function nextDueAt(topics: ReviewTopic[], now: number): string | null {
  let best: string | null = null;
  let bestMs = Number.POSITIVE_INFINITY;
  for (const topic of topics) {
    if (!topic.enabled) continue;
    for (const entry of topic.schedule) {
      const at = Date.parse(entry.dueAt);
      if (Number.isFinite(at) && at > now && at < bestMs) {
        bestMs = at;
        best = entry.dueAt;
      }
    }
  }
  return best;
}

/**
 * Locally marks one of a topic's due exercises as no longer due, so the tree and
 * the badge drop as answers are given. The replacement entry is a placeholder —
 * the real next due time comes from the server (the interval ladder lives only
 * there) and lands on the next fetch.
 */
function consumeDue(topic: ReviewTopic, now: number): ReviewTopic {
  const idx = topic.schedule.findIndex((d) => isOffered(dueState(d.dueAt, d.expiresAt, now)));
  if (idx < 0) return topic;
  const placeholder: ReviewDue = {
    dueAt: new Date(now + CLOCK_TICK_MS).toISOString(),
    expiresAt: new Date(now + CLOCK_TICK_MS * 2).toISOString(),
  };
  const schedule = [...topic.schedule];
  schedule[idx] = placeholder;
  return { ...topic, schedule };
}

// --- domain scoping ---------------------------------------------------------

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
 * How many exercises the active domain has due right now — the header badge.
 * Counts due exercises of enabled topics only, which is exactly what a review
 * run would ask. 0 until topic summaries load, since without them the domain of
 * a pooled topic is unknown.
 */
export function useReviewBadge(): number {
  const pool = useReview((s) => s.pool);
  const clock = useReview((s) => s.clock);
  const summaries = useStore((s) => s.topics);
  const domainId = useDomain((s) => s.domainId);
  return useMemo(() => {
    if (summaries.length === 0) return 0;
    const inDomain = domainFilter(summaries, domainId);
    return pool.reduce(
      (n, t) => (t.enabled && inDomain(t.topicId) ? n + topicCounts(t, clock).due : n),
      0,
    );
  }, [pool, summaries, domainId, clock]);
}

// --- the wall clock ---------------------------------------------------------

/**
 * What is due grows on its own as time passes, unlike the old "answered or not"
 * flag. Without this the badge would read 0 all night while forty exercises came
 * due behind it.
 */
let clockStarted = false;
function startClock(): void {
  if (clockStarted || typeof window === 'undefined') return;
  clockStarted = true;
  const tick = () => useReview.setState({ clock: Date.now() });
  setInterval(tick, CLOCK_TICK_MS);
  document.addEventListener('visibilitychange', tick);
  window.addEventListener('focus', tick);
}

// --- run building -----------------------------------------------------------

/** Fisher-Yates shuffle (returns a new array). */
function shuffled<T>(arr: T[]): T[] {
  const out = [...arr];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

interface Built {
  queue: ReviewItem[];
  offered: number;
  topics: ReviewTopic[];
  pool: ReviewTopic[];
}

/**
 * Fetches everything and builds the run.
 *
 * The cap is applied AFTER the domain filter on purpose: capping on the server
 * would hand back thirty ndm exercises while the user is in the java domain and
 * leave the screen claiming there is nothing to review. Within the cap the most
 * overdue win, so a backlog drains oldest-first, but the batch itself is
 * shuffled so it does not replay in pool order.
 *
 * Items already answered in this run are excluded: their answers are still
 * travelling through the outbox, so the server would otherwise hand them
 * straight back.
 */
async function build(
  mode: ReviewMode,
  aheadTopicId: string | null,
  preserve: ReviewItem | null,
  gradedIds: Set<string>,
): Promise<Built> {
  const [allItems, allTopics] = await Promise.all([fetchReviewList(), fetchReviewTopics()]);
  const inDomain = activeDomainFilter();
  const now = Date.now();
  const enabled = new Set(allTopics.filter((t) => t.enabled).map((t) => t.topicId));

  const eligible = allItems.filter((item) => {
    if (gradedIds.has(itemKey(item))) return false;
    const state = itemState(item, now);
    if (mode === 'ahead') return item.topicId === aheadTopicId && !isOffered(state);
    return inDomain(item.topicId) && enabled.has(item.topicId) && isOffered(state);
  });

  const keep = preserve && eligible.some((i) => sameItem(i, preserve)) ? preserve : null;
  const rest = keep ? eligible.filter((i) => !sameItem(i, keep)) : eligible;
  const byUrgency = [...rest].sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt));
  const batch = shuffled(byUrgency.slice(0, SESSION_SIZE - (keep ? 1 : 0)));

  return {
    queue: keep ? [keep, ...batch] : batch,
    offered: eligible.length,
    topics: allTopics.filter((t) => inDomain(t.topicId)),
    pool: allTopics,
  };
}

export const useReview = create<ReviewSlice>((set, get) => ({
  pool: [],
  topics: [],
  queue: [],
  offered: 0,
  mode: 'due',
  aheadTopicId: null,
  loading: false,
  error: null,
  phase: 'answering',
  lastCorrect: false,
  lastAnswer: null,
  answered: 0,
  gradedIds: new Set(),
  lastWasFirst: false,
  clock: Date.now(),

  async loadPool() {
    startClock();
    try {
      set({ pool: await fetchReviewTopics(), clock: Date.now() });
    } catch {
      /* badge is a nice-to-have */
    }
  },

  async start() {
    startClock();
    set({
      loading: true,
      error: null,
      phase: 'answering',
      lastAnswer: null,
      answered: 0,
      mode: 'due',
      aheadTopicId: null,
      gradedIds: new Set(),
    });
    try {
      const built = await build('due', null, null, new Set());
      set({ ...built, loading: false, clock: Date.now() });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  async continueRun() {
    const s = get();
    set({ loading: true, error: null, phase: 'answering', lastAnswer: null });
    try {
      const built = await build(s.mode, s.aheadTopicId, null, s.gradedIds);
      set({ ...built, loading: false, clock: Date.now() });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  async startAhead(topicId) {
    startClock();
    set({
      loading: true,
      error: null,
      phase: 'answering',
      lastAnswer: null,
      answered: 0,
      mode: 'ahead',
      aheadTopicId: topicId,
      gradedIds: new Set(),
    });
    try {
      const built = await build('ahead', topicId, null, new Set());
      set({ ...built, loading: false, clock: Date.now() });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  async submit(answer) {
    const s = get();
    const item = currentReviewItem(s.queue);
    if (!item) return;

    const correct = grade(item.exercise, answer, useLang.getState().lang);
    const key = itemKey(item);
    const firstAttempt = !s.gradedIds.has(key);

    // Only the first attempt moves the schedule; the retry after a mistake is
    // practice, not a second verdict. An ahead-of-schedule drill never moves it.
    if (firstAttempt && s.mode === 'due') {
      void markReviewAnswer(item.topicId, item.exercise.id, correct);
    }
    // The answer log records every attempt either way.
    void saveExerciseAnswer(item.topicId, {
      exerciseId: item.exercise.id,
      atomId: item.atomId,
      unitId: '',
      context: 'review',
      answer,
      correct,
    });

    set({
      phase: 'feedback',
      lastCorrect: correct,
      lastAnswer: answer,
      lastWasFirst: firstAttempt,
      answered: firstAttempt ? s.answered + 1 : s.answered,
      gradedIds: firstAttempt ? new Set(s.gradedIds).add(key) : s.gradedIds,
    });
  },

  next() {
    const s = get();
    const [head, ...tail] = s.queue;
    if (head === undefined) {
      set({ phase: 'answering', lastAnswer: null });
      return;
    }
    // Correct → done with it for now; wrong → back to the tail to be retried
    // this run. Either way it has left the due list: a mistake reschedules it
    // onto the first rung rather than keeping it up.
    const queue = s.lastCorrect ? tail : [...tail, head];
    const now = Date.now();
    const drop = (list: ReviewTopic[]) =>
      list.map((t) => (t.topicId === head.topicId ? consumeDue(t, now) : t));
    const counted = s.mode === 'due' && s.lastWasFirst;
    set({
      queue,
      topics: counted ? drop(s.topics) : s.topics,
      pool: counted ? drop(s.pool) : s.pool,
      phase: 'answering',
      lastAnswer: null,
      clock: now,
    });
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

  async resetSchedule() {
    try {
      await restartReviewAll();
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
    set({ answered: 0, gradedIds: new Set(), mode: 'due', aheadTopicId: null });
    await get().reload(false);
  },

  async reload(preserveCurrent) {
    const s = get();
    try {
      const preserve = preserveCurrent ? currentReviewItem(s.queue) : null;
      // Keep the mode: reloading after a topic toggle must not turn an
      // ahead-of-schedule drill back into a scheduled run.
      const built = await build(s.mode, s.aheadTopicId, preserve, s.gradedIds);
      set({ ...built, phase: 'answering', lastAnswer: null, clock: Date.now() });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  reset() {
    set({
      queue: [],
      offered: 0,
      error: null,
      phase: 'answering',
      lastAnswer: null,
      answered: 0,
      mode: 'due',
      aheadTopicId: null,
      gradedIds: new Set(),
    });
  },
}));
