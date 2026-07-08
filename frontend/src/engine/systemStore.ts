import { create } from 'zustand';
import { fetchSystemStatus, postSystemUpdate, type SystemStatus } from './api';

/**
 * Polls /api/system/status for the settings gear: whether rebuild/restart is
 * possible here and how many commits the GitHub upstream is ahead (the badge).
 * The backend fetches on its own schedule and caches, so client polling is cheap.
 *
 * `triggerUpdate` kicks off a tray-driven rebuild+restart. The backend goes down
 * during the rebuild, so we can't stream progress; instead we poll for the
 * server to come back with a *changed* bootId and then reload the page.
 */
const POLL_MS = 60_000;
const RESTART_POLL_MS = 2_000;
const RESTART_TIMEOUT_MS = 6 * 60_000;

let started = false;
let timer: ReturnType<typeof setInterval> | null = null;

interface SystemState {
  status: SystemStatus | null;
  /** True from the moment an update is requested until the page reloads. */
  updating: boolean;
  /** Set if the server didn't come back in time; the overlay offers a manual reload. */
  updateError: boolean;
  start: () => void;
  refresh: () => Promise<void>;
  triggerUpdate: (pull: boolean) => Promise<void>;
}

export const useSystem = create<SystemState>((set, get) => ({
  status: null,
  updating: false,
  updateError: false,
  refresh: async () => {
    try {
      const status = await fetchSystemStatus();
      set({ status });
    } catch {
      // Transient failure — keep the previous reading.
    }
  },
  start: () => {
    if (started) return;
    started = true;
    void get().refresh();
    timer = setInterval(() => void get().refresh(), POLL_MS);
  },
  triggerUpdate: async (pull: boolean) => {
    if (get().updating) return;
    const prevBootId = get().status?.bootId ?? null;
    set({ updating: true, updateError: false });
    try {
      await postSystemUpdate(pull);
    } catch {
      set({ updating: false, updateError: false });
      return;
    }
    // Stop the normal poller so the two don't interleave.
    if (timer) clearInterval(timer);
    timer = null;
    started = false;

    const deadline = Date.now() + RESTART_TIMEOUT_MS;
    const poll = setInterval(async () => {
      if (Date.now() > deadline) {
        clearInterval(poll);
        set({ updateError: true });
        return;
      }
      try {
        const status = await fetchSystemStatus();
        // A different bootId means the freshly built server is up.
        if (status.bootId && status.bootId !== prevBootId) {
          clearInterval(poll);
          window.location.reload();
        }
      } catch {
        // Server is down mid-rebuild; keep polling.
      }
    }, RESTART_POLL_MS);
  },
}));
