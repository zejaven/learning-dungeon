import { create } from 'zustand';
import { fetchUsage, type UsageSnapshot } from './api';

/**
 * Polls the local /api/usage endpoint for Claude session/weekly usage. The
 * backend caches and throttles the real upstream call (safe only ~every 180s),
 * so polling here every minute is cheap and just reflects the cached reading.
 *
 * A single module-level interval is shared across screens; mounting either the
 * Home or Workspace header doesn't spin up a second poller.
 */
const POLL_MS = 60_000;

let started = false;
let timer: ReturnType<typeof setInterval> | null = null;

interface UsageState {
  snapshot: UsageSnapshot | null;
  /** Starts polling (idempotent). Safe to call from every screen's effect. */
  start: () => void;
  refresh: () => Promise<void>;
}

export const useUsage = create<UsageState>((set, get) => ({
  snapshot: null,
  refresh: async () => {
    try {
      const snap = await fetchUsage();
      set({ snapshot: snap });
    } catch {
      // Transient failure — keep the previous reading rather than blanking it.
    }
  },
  start: () => {
    if (started) return;
    started = true;
    void get().refresh();
    timer = setInterval(() => void get().refresh(), POLL_MS);
  },
}));

/** Stops the shared poller (not used in-app; handy for tests/HMR cleanup). */
export function stopUsagePolling() {
  if (timer) clearInterval(timer);
  timer = null;
  started = false;
}
