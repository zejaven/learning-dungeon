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

async function enqueue(op: OutboxOp): Promise<void> {
  if (!idbAvailable()) return; // nothing we can do; the write is simply lost
  try {
    await run('readwrite', (store) => store.add(op) as IDBRequest<IDBValidKey>);
    changed();
  } catch {
    /* a full or unavailable database must not break the lesson */
  }
}

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
    return await run<number>('readonly', (store) => store.count());
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
 * Sends the write, queueing it if the network refuses. A 4xx other than 401 is
 * the server rejecting the content itself: replaying it forever would wedge the
 * queue, so it is dropped.
 */
export async function send(op: OutboxOp): Promise<void> {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    await enqueue(op);
    return;
  }
  try {
    const res = await fetch(op.url, requestInit(op));
    reportReachable?.(true);
    if (res.ok) return;
    if (res.status >= 400 && res.status < 500 && res.status !== 401 && res.status !== 408) return;
    await enqueue(op);
  } catch {
    reportReachable?.(false);
    await enqueue(op);
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
  const entries = await all();
  const topics = new Set<string>();
  let sent = 0;

  for (const op of entries) {
    let ok = false;
    let drop = false;
    try {
      const res = await fetch(op.url, requestInit(op));
      reportReachable?.(true);
      ok = res.ok;
      drop = !res.ok && res.status >= 400 && res.status < 500 && res.status !== 401 && res.status !== 408;
    } catch {
      reportReachable?.(false);
      break; // still offline: keep this entry and everything after it
    }
    if (!ok && !drop) break; // 5xx / 401: try again later, in order

    if (op.id != null) await remove(op.id);
    if (ok) {
      sent++;
      if (op.topicId) topics.add(op.topicId);
    }
  }

  const remaining = await pendingCount();
  if (sent > 0 || entries.length !== remaining) changed();
  return { sent, remaining, topics: [...topics] };
}
