import { create } from 'zustand';
import { fetchUsage, type UsageSnapshot } from './api';
import { useAi } from './aiStore';

/**
 * Polls the local /api/usage endpoint for the selected provider. The backend
 * caches and throttles any upstream calls, so polling every minute is cheap.
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
      const snap = await fetchUsage(useAi.getState().selectedProvider);
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
