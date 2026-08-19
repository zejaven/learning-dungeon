import { OUTBOX_STORE, idbAvailable, run } from './db';

/**
 * Write queue for progress the app records while it cannot reach the backend.
 *
 * Only writes that are pure persistence belong here — the ones whose response
 * the UI does not need. Lesson and review grading is deterministic and happens
 * on the client, so an answer is already "applied" the moment it is graded; the
 * POST is a log entry the server can receive minutes later without changing
 * anything the learner sees. Anything that needs a server answer to proceed
 * (AI grading, generation, running code) is online-only and is NOT queued.
 *
 * Entries replay in insertion order, so re-answering the same exercise ends up
 * with the last answer winning on the server too.
 */

export type OutboxKind = 'lesson-answer' | 'review-answer' | 'review-topic' | 'review-restart';

/** A pending write, exactly as it will be replayed. */
export interface OutboxOp {
  id?: number;
  kind: OutboxKind;
  url: string;
  /** Omitted for POSTs that carry no body (the "start again" endpoints). */
  body?: unknown;
  /** Topic the write belongs to; drives the lesson overlay and post-sync refresh. */
  topicId?: string;
  /**
   * For 'lesson-answer' only: what the lesson store needs to show this answer
   * again after a cold start that never reached the server.
   */
  answer?: { exerciseId: string; answerJson: string; correct: boolean };
  ts: number;
}

type Listener = () => void;
const listeners = new Set<Listener>();

/** Notified whenever the queue length changes (drives the header indicator). */
export function onOutboxChange(fn: Listener): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function changed(): void {
  for (const fn of listeners) fn();
}

/**
 * Reports whether the backend answered. Every write goes through here, which
 * makes it the earliest and most reliable signal that the PC is off — much
 * better than navigator.onLine, which only knows about the Wi-Fi. Set by
 * offlineStore; kept as a callback so this module has no dependency on it.
 */
let reportReachable: ((ok: boolean) => void) | null = null;

export function setReachabilityReporter(fn: (ok: boolean) => void): void {
  reportReachable = fn;
}

/**
 * Called with the topics the server has just accepted writes for. Their cached
 * lesson state is now out of date by exactly those answers, and a stale copy is
 * what makes progress look lost the next time the app opens offline.
 */
let reportFlushed: ((topics: string[]) => void) | null = null;

export function setFlushedListener(fn: (topics: string[]) => void): void {
  reportFlushed = fn;
}

async function enqueue(op: OutboxOp, notify = true): Promise<number | null> {
  if (!idbAvailable()) return null; // nothing we can do; the write is simply lost
  try {
    const key = await run('readwrite', (store) => store.add(op) as IDBRequest<IDBValidKey>);
    if (notify) changed();
    return typeof key === 'number' ? key : null;
  } catch {
    /* a full or unavailable database must not break the lesson */
    return null;
  }
}

/**
 * Entries currently being sent. They are already in the store (that is the
 * point), but showing them in the header would make the badge blink on every
 * single answer, so the count ignores them.
 */
const inFlight = new Set<number>();

export async function all(): Promise<OutboxOp[]> {
  if (!idbAvailable()) return [];
  try {
    const entries = await run<OutboxOp[]>('readonly', (store) => store.getAll() as IDBRequest<OutboxOp[]>);
    return entries.sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
  } catch {
    return [];
  }
}

export async function pendingCount(): Promise<number> {
  if (!idbAvailable()) return 0;
  try {
    const total = await run<number>('readonly', (store) => store.count());
    return Math.max(0, total - inFlight.size);
  } catch {
    return 0;
  }
}

async function remove(id: number): Promise<void> {
  try {
    await run('readwrite', (store) => store.delete(id) as unknown as IDBRequest<undefined>);
  } catch {
    /* ignore */
  }
}

export async function clear(): Promise<void> {
  if (!idbAvailable()) return;
  try {
    await run('readwrite', (store) => store.clear() as unknown as IDBRequest<undefined>);
    changed();
  } catch {
    /* ignore */
  }
}

/** The answers this topic has queued, oldest first (later ones overwrite earlier). */
export async function pendingAnswers(topicId: string): Promise<OutboxOp['answer'][]> {
  const entries = await all();
  return entries
    .filter((op) => op.kind === 'lesson-answer' && op.topicId === topicId && op.answer)
    .map((op) => op.answer);
}

function requestInit(op: OutboxOp): RequestInit {
  if (op.body === undefined) return { method: 'POST' };
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(op.body),
  };
}

/**
 * How long a write may take before the backend counts as unreachable. An
 * unreachable host does not refuse the connection — it swallows the packets, so
 * without a deadline the request hangs for minutes and reports nothing.
 */
const SEND_TIMEOUT_MS = 8000;

async function post(op: OutboxOp): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), SEND_TIMEOUT_MS);
  try {
    return await fetch(op.url, { ...requestInit(op), signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/**
 * The server rejected the CONTENT itself, so replaying it forever would wedge
 * the queue and it is dropped instead.
 *
 * Deliberately narrow. A 401/403 is about who is asking, not about what was
 * sent: an expired token cookie, or a proxy the backend has been misconfigured
 * to distrust. Treating those as permanent is how a month of answers can
 * disappear without a trace, so they stay queued and stay visible.
 */
function permanentFailure(status: number): boolean {
  const retryable = [401, 403, 408, 425, 429];
  return status >= 400 && status < 500 && !retryable.includes(status);
}

/**
 * Records the write, then delivers it.
 *
 * The order is the whole point. Storing the entry BEFORE touching the network
 * means a request that never returns cannot take the answer with it: reload the
 * page mid-flight, kill the app, close the lid — the answer is already on disk
 * and the next flush sends it. Delivery goes through the same ordered flush as
 * the backlog, so a fresh answer can never overtake an older queued one.
 */
export async function send(op: OutboxOp): Promise<void> {
  const id = await enqueue(op, false);
  if (id == null) {
    // No IndexedDB (private mode, quota): best effort, nothing to fall back on.
    try {
      await post(op);
      reportReachable?.(true);
    } catch {
      reportReachable?.(false);
    }
    return;
  }
  // Registered as in-flight before anyone is notified, so the header does not
  // blink a "pending write" badge on every single answer.
  inFlight.add(id);
  changed();
  try {
    await flush();
  } finally {
    inFlight.delete(id);
    changed();
  }
}

export interface FlushResult {
  sent: number;
  /** Left in the queue — the network gave up again. */
  remaining: number;
  /** Topics whose lesson state changed on the server and should be refetched. */
  topics: string[];
}

let flushing: Promise<FlushResult> | null = null;

/**
 * Replays the queue in order. Stops at the first network failure so ordering is
 * preserved; the next online event picks it up again. Concurrent calls share
 * one run.
 */
export function flush(): Promise<FlushResult> {
  if (flushing) return flushing;
  flushing = doFlush().finally(() => {
    flushing = null;
  });
  return flushing;
}

async function doFlush(): Promise<FlushResult> {
  const topics = new Set<string>();
  let sent = 0;

  // The one case we can be sure of without spending a timeout on it.
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return { sent, remaining: await pendingCount(), topics: [] };
  }

  // Answers given while a pass is running land behind it; the next pass takes
  // them. Bounded so a pathological loop cannot spin forever.
  for (let pass = 0; pass < 50; pass++) {
    const entries = await all();
    if (entries.length === 0) break;

    let progressed = false;
    let stop = false;
    for (const op of entries) {
      let ok = false;
      let drop = false;
      try {
        const res = await post(op);
        reportReachable?.(true);
        ok = res.ok;
        drop = !res.ok && permanentFailure(res.status);
        if (drop) console.warn('[outbox] dropping a rejected write', res.status, op.url);
      } catch {
        reportReachable?.(false);
        stop = true; // still unreachable: keep this entry and everything after it
        break;
      }
      if (!ok && !drop) {
        stop = true; // 5xx / 401: try again later, in order
        break;
      }
      if (op.id != null) {
        await remove(op.id);
        inFlight.delete(op.id);
      }
      progressed = true;
      if (ok) {
        sent++;
        if (op.topicId) topics.add(op.topicId);
      }
    }
    if (stop || !progressed) break;
  }

  const remaining = await pendingCount();
  changed();
  if (topics.size > 0) reportFlushed?.([...topics]);
  return { sent, remaining, topics: [...topics] };
}
