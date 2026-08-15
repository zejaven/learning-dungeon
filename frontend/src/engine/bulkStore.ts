import { create } from 'zustand';
import {
  dismissBulk,
  fetchBulkStatus,
  startBulk,
  stopBulk,
  type BulkStatus,
  type StartBulkBody,
} from './api';
import { genLanguages } from './genLangStore';
import { useGeneration } from './generationStore';
import { useStore } from './store';

/**
 * Client view of the backend bulk-generation loop. The loop itself runs on the
 * server (it survives reloads and closed tabs); this store only polls
 * /api/bulk/status while a run exists and drives the header progress strip.
 *
 * On item progress it reattaches the per-item SSE views (so opening a topic
 * shows the live generation log) and reloads the topic list (so the tree links
 * fresh topics / flips hasAtoms).
 */
const POLL_MS = 3_000;

let timer: ReturnType<typeof setInterval> | null = null;

function stopPolling() {
  if (timer) clearInterval(timer);
  timer = null;
}

interface BulkState {
  status: BulkStatus | null;
  /** True while the start POST is in flight. */
  starting: boolean;
  /** Server refusal message for the start dialog; null when the last start worked. */
  error: string | null;
  /** One-shot check on app mount: resume polling if a run is already active. */
  checkOnce: () => Promise<void>;
  start: (body: StartBulkBody) => Promise<boolean>;
  stop: () => Promise<void>;
  dismiss: () => Promise<void>;
}

export const useBulk = create<BulkState>((set, get) => {
  function apply(next: BulkStatus) {
    const prev = get().status;
    set({ status: next });
    const prevRun = prev?.run;
    const run = next.run;
    if (run && (!prevRun
      || prevRun.currentIndex !== run.currentIndex
      || prevRun.items.some((it, i) => it.status !== run.items[i]?.status))) {
      // A new item started or one finished: reattach SSE logs + refresh topics.
      void useGeneration.getState().refreshActive();
      void useStore.getState().loadTopics();
    }
    if (!next.active) stopPolling();
  }

  async function poll() {
    try {
      apply(await fetchBulkStatus());
    } catch {
      // Transient failure (e.g. backend restarting) — keep the previous view.
    }
  }

  function startPolling() {
    if (timer) return;
    timer = setInterval(() => void poll(), POLL_MS);
  }

  return {
    status: null,
    starting: false,
    error: null,
    checkOnce: async () => {
      try {
        const status = await fetchBulkStatus();
        if (status.run) {
          apply(status);
          if (status.active) startPolling();
        }
      } catch {
        /* no strip on failure */
      }
    },
    start: async (body: StartBulkBody) => {
      if (get().starting) return false;
      set({ starting: true, error: null });
      try {
        const status = await startBulk({ ...body, languages: genLanguages() });
        set({ status, starting: false });
        startPolling();
        return true;
      } catch (e) {
        set({ starting: false, error: e instanceof Error ? e.message : String(e) });
        return false;
      }
    },
    stop: async () => {
      try {
        apply(await stopBulk());
      } catch {
        /* the next poll shows the real state */
      }
    },
    dismiss: async () => {
      await dismissBulk().catch(() => undefined);
      stopPolling();
      set({ status: null });
    },
  };
});
