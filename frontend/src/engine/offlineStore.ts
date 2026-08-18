import { create } from 'zustand';
import { domainOf } from '../domains';
import { fetchTopics } from './api';
import {
  API_CACHE,
  ASSET_CACHE,
  cacheAvailable,
  clearData,
  storedResponses,
  usedBytes,
  warm,
} from './offline/cache';
import {
  clear as clearOutbox,
  flush,
  onOutboxChange,
  pendingCount,
  setReachabilityReporter,
} from './offline/outbox';

/**
 * Offline state of the app: whether the backend is reachable, how many writes
 * are waiting to be replayed, and the "download this domain" job that fills the
 * cache the service worker serves from.
 *
 * Downloading is explicit on purpose. Runtime caching alone would only ever
 * hold the topics you already opened, which is exactly the wrong set when you
 * are about to lose connectivity.
 */
interface OfflineSlice {
  /** What the UI reacts to: there is a network AND the backend answers on it. */
  online: boolean;
  /** navigator.onLine — knows about the Wi-Fi, not about the PC being asleep. */
  networkUp: boolean;
  /** Whether the last contact with the backend succeeded. */
  reachable: boolean;
  /** Writes queued while offline (lesson/review answers). */
  pending: number;
  syncing: boolean;
  /** Cached API responses — 0 means nothing was downloaded yet. */
  stored: number;
  bytes: number | null;
  download: {
    running: boolean;
    done: number;
    total: number;
    /** Domain being downloaded, for the label. */
    domainId: string | null;
    failed: number;
  };

  init: () => void;
  /**
   * Records the outcome of a backend call. The interesting offline case for
   * this app is not "no signal" but "the PC is off while the phone is happily
   * on Wi-Fi", which navigator.onLine reports as online.
   */
  reportBackend: (ok: boolean) => void;
  /** Asks the backend whether it is there, right now. */
  ping: () => Promise<void>;
  refresh: () => Promise<void>;
  sync: () => Promise<void>;
  downloadDomain: (domainId: string) => Promise<void>;
  clearDownloaded: () => Promise<void>;
}

/** Image links a topic explanation embeds, as asset URLs of that topic. */
export function assetUrls(topicId: string, explanation: unknown): string[] {
  const texts: string[] = [];
  if (typeof explanation === 'string') texts.push(explanation);
  else if (explanation && typeof explanation === 'object') {
    for (const value of Object.values(explanation as Record<string, unknown>)) {
      if (typeof value === 'string') texts.push(value);
    }
  }
  const out = new Set<string>();
  for (const text of texts) {
    // Markdown image links with a relative target, e.g. ![x](images/net.png).
    for (const m of text.matchAll(/!\[[^\]]*\]\((?!https?:|\/|data:)([^)\s]+)\)/g)) {
      const rel = m[1].replace(/^\.\//, '');
      out.add(`/api/topics/${encodeURIComponent(topicId)}/assets/${rel}`);
    }
  }
  return [...out];
}

/** Everything the read-only screens of one domain need while offline. */
export function domainApiUrls(
  topics: { id: string; hasAtoms: boolean }[],
): string[] {
  const urls = ['/api/topics', '/api/questions', '/api/review/list', '/api/review/topics'];
  for (const t of topics) {
    const id = encodeURIComponent(t.id);
    urls.push(`/api/topics/${id}`, `/api/topics/${id}/versions`, `/api/progress/${id}`);
    if (t.hasAtoms) urls.push(`/api/topics/${id}/atoms`, `/api/lesson/${id}/state`);
  }
  return urls;
}

export const useOffline = create<OfflineSlice>((set, get) => ({
  online: typeof navigator === 'undefined' ? true : navigator.onLine,
  networkUp: typeof navigator === 'undefined' ? true : navigator.onLine,
  reachable: true,
  pending: 0,
  syncing: false,
  stored: 0,
  bytes: null,
  download: { running: false, done: 0, total: 0, domainId: null, failed: 0 },

  init() {
    window.addEventListener('online', () => {
      // A regained network says nothing about the backend yet; a successful
      // call (or a drained queue) is what flips `reachable` back.
      set({ networkUp: true, online: get().reachable });
      void get().sync();
    });
    window.addEventListener('offline', () => set({ networkUp: false, online: false }));
    setReachabilityReporter((ok) => get().reportBackend(ok));
    // Coming back to the app is exactly when the answer may have changed —
    // the phone was in a pocket, the PC went to sleep. Losing a VPN or a
    // tunnel raises no browser event at all, so without this the header would
    // keep claiming everything is fine until the next status poll (a minute).
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') void get().ping();
    });
    onOutboxChange(() => void get().refresh());
    void get().refresh();
    // Anything queued by a previous session goes out as soon as we start.
    if (navigator.onLine) void get().sync();
  },

  reportBackend(ok) {
    const s = get();
    if (s.reachable === ok) return;
    set({ reachable: ok, online: ok && s.networkUp });
    if (ok) void get().sync(); // back in touch: push what piled up
  },

  async ping() {
    try {
      // /api/system/ is excluded from the service worker's caches, so this
      // always reflects the network rather than a stored copy.
      const res = await fetch('/api/system/status', { cache: 'no-store' });
      get().reportBackend(res.ok);
    } catch {
      get().reportBackend(false);
    }
  },

  async refresh() {
    const [pending, stored, bytes] = await Promise.all([
      pendingCount(),
      storedResponses(),
      usedBytes(),
    ]);
    set({ pending, stored, bytes });
  },

  async sync() {
    if (get().syncing) return;
    set({ syncing: true });
    try {
      const result = await flush();
      // The server now knows about these answers, but the cached lesson state
      // still predates them: refresh it so a later offline start is correct.
      for (const topicId of result.topics) {
        try {
          await fetch(`/api/lesson/${encodeURIComponent(topicId)}/state`, {
            credentials: 'same-origin',
            cache: 'reload',
          });
        } catch {
          /* the next online load will do it */
        }
      }
    } finally {
      set({ syncing: false });
      await get().refresh();
    }
  },

  async downloadDomain(domainId) {
    if (get().download.running || !cacheAvailable()) return;
    set({ download: { running: true, done: 0, total: 0, domainId, failed: 0 } });
    try {
      const all = await fetchTopics();
      const topics = all.filter((t) => domainOf(t) === domainId);
      const urls = domainApiUrls(topics);
      set((s) => ({ download: { ...s.download, total: urls.length } }));

      const api = await warm(urls, API_CACHE, (done, total) =>
        set((s) => ({ download: { ...s.download, done, total } })),
      );

      // Explanation images are only discoverable from the details we just
      // stored, so collect them from the cache instead of fetching twice.
      const assets: string[] = [];
      const cache = await caches.open(API_CACHE);
      for (const t of topics) {
        const hit = await cache.match(`/api/topics/${encodeURIComponent(t.id)}`);
        if (!hit) continue;
        try {
          const detail = await hit.json();
          assets.push(...assetUrls(t.id, detail?.explanation));
        } catch {
          /* a malformed cached entry is not worth failing the download over */
        }
      }
      let assetFailed = 0;
      if (assets.length > 0) {
        set((s) => ({ download: { ...s.download, done: urls.length, total: urls.length + assets.length } }));
        const res = await warm(assets, ASSET_CACHE, (done) =>
          set((s) => ({ download: { ...s.download, done: urls.length + done } })),
        );
        assetFailed = res.failed;
      }
      set((s) => ({ download: { ...s.download, failed: api.failed + assetFailed } }));
    } catch {
      set((s) => ({ download: { ...s.download, failed: s.download.failed + 1 } }));
    } finally {
      set((s) => ({ download: { ...s.download, running: false } }));
      await get().refresh();
    }
  },

  async clearDownloaded() {
    await clearData();
    await clearOutbox();
    await get().refresh();
  },
}));
