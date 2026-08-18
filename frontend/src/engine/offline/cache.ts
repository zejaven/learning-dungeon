/**
 * Filling (and measuring, and emptying) the caches the service worker serves
 * from when the backend is unreachable.
 *
 * The names below are the ones declared in `vite.config.ts` — the plugin config
 * and this file are two halves of the same contract: the worker decides what a
 * request falls back to, this decides what is there before you ever open it.
 */

export const API_CACHE = 'jid-api';
export const ASSET_CACHE = 'jid-topic-assets';

/** Requests in flight at once while downloading a domain. */
const CONCURRENCY = 6;

export function cacheAvailable(): boolean {
  return typeof caches !== 'undefined';
}

/**
 * Fetches each URL and stores the response. The service worker would cache
 * these too as they pass through, but doing it explicitly means a download
 * still works on the very first visit, before the worker controls the page.
 */
export async function warm(
  urls: string[],
  cacheName: string,
  onProgress?: (done: number, total: number) => void,
): Promise<{ ok: number; failed: number }> {
  if (!cacheAvailable() || urls.length === 0) return { ok: 0, failed: urls.length };
  const cache = await caches.open(cacheName);

  let done = 0;
  let ok = 0;
  let failed = 0;
  const queue = [...urls];

  async function worker(): Promise<void> {
    for (;;) {
      const url = queue.shift();
      if (url === undefined) return;
      try {
        const res = await fetch(url, { credentials: 'same-origin' });
        if (res.ok) {
          await cache.put(url, res.clone());
          ok++;
        } else {
          failed++;
        }
      } catch {
        failed++;
      }
      done++;
      onProgress?.(done, urls.length);
    }
  }

  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, urls.length) }, worker));
  return { ok, failed };
}

/** How many bytes this origin currently holds, when the browser will say. */
export async function usedBytes(): Promise<number | null> {
  try {
    const estimate = await navigator.storage?.estimate?.();
    return estimate?.usage ?? null;
  } catch {
    return null;
  }
}

/** Drops the downloaded data (not the app shell — that is the worker's own cache). */
export async function clearData(): Promise<void> {
  if (!cacheAvailable()) return;
  await Promise.all([caches.delete(API_CACHE), caches.delete(ASSET_CACHE)]);
}

/** Number of stored API responses, as a rough "is anything downloaded" signal. */
export async function storedResponses(): Promise<number> {
  if (!cacheAvailable()) return 0;
  try {
    const cache = await caches.open(API_CACHE);
    return (await cache.keys()).length;
  } catch {
    return 0;
  }
}
