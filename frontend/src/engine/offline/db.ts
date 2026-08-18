/**
 * Minimal IndexedDB access for the offline outbox. Deliberately dependency-free
 * and tiny: one database, one object store, promise wrappers over the four
 * operations the outbox needs.
 */

const DB_NAME = 'jid-offline';
const DB_VERSION = 1;
export const OUTBOX_STORE = 'outbox';

let dbPromise: Promise<IDBDatabase> | null = null;

/** True in a browser that can persist writes made while offline. */
export function idbAvailable(): boolean {
  return typeof indexedDB !== 'undefined';
}

export function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  const opening = new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(OUTBOX_STORE)) {
        // Auto-increment key = replay order: an answer must not overtake the
        // answer that came before it.
        db.createObjectStore(OUTBOX_STORE, { keyPath: 'id', autoIncrement: true });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  }).catch((e: unknown) => {
    dbPromise = null; // let a later call retry (private mode, quota, ...)
    throw e;
  });
  dbPromise = opening;
  return opening;
}

/** Runs one request against a store and resolves with its result. */
export async function run<T>(
  mode: IDBTransactionMode,
  action: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await openDb();
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction(OUTBOX_STORE, mode);
    const request = action(tx.objectStore(OUTBOX_STORE));
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    tx.onabort = () => reject(tx.error);
  });
}
