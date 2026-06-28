package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic teaching model that contrasts a synchronized HashMap
 * (Collections.synchronizedMap: one table-wide monitor) with a
 * ConcurrentHashMap (per-bin lock striping plus lock-free reads).
 *
 * It simulates named threads and locks so examples stay stable and do not depend
 * on real scheduler timing. A blocked thread does not actually park the JVM; the
 * example re-attempts the lock after the owner releases it.
 */
public class VisualConcurrentMap {

    private static final int CAPACITY = 8;
    private static final int HISTORY_LIMIT = 7;

    private final String name;
    private final String kind;
    private final String strategy;
    private final List<Bin> bins = new ArrayList<>();
    private final Map<String, ThreadState> threads = new LinkedHashMap<>();
    private final Set<String> waiting = new LinkedHashSet<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    /** Owner of the single table-wide lock; only used by a synchronized map. */
    private String mapLockOwner;

    private VisualConcurrentMap(String name, String kind, String strategy) {
        this.name = name;
        this.kind = kind;
        this.strategy = strategy;
        for (int i = 0; i < CAPACITY; i++) {
            bins.add(new Bin(i));
        }
    }

    public static VisualConcurrentMap synchronizedMap(String name) {
        VisualConcurrentMap map = new VisualConcurrentMap(
                name,
                "SYNCHRONIZED_MAP",
                "one table-wide monitor guards every method, reads included");
        map.created("Created synchronized map '" + name
                        + "': every get/put locks the one shared monitor",
                "Создана synchronized map '" + name
                        + "': каждый get/put берёт один общий monitor");
        return map;
    }

    public static VisualConcurrentMap concurrentHashMap(String name) {
        VisualConcurrentMap map = new VisualConcurrentMap(
                name,
                "CONCURRENT_HASH_MAP",
                "per-bin lock striping for writes, lock-free reads");
        map.created("Created ConcurrentHashMap '" + name
                        + "': writes lock only one bin, reads take no lock",
                "Создана ConcurrentHashMap '" + name
                        + "': запись блокирует только один bin, чтение без lock");
        return map;
    }

    /** Bin index for a key, mirroring HashMap's spread + power-of-two masking. */
    public static int bin(String key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return Math.floorMod(h, CAPACITY);
    }

    // ---- Synchronized map: one global monitor ------------------------------

    public void lock(String thread, String operation) {
        requireKind("SYNCHRONIZED_MAP");
        ThreadState state = thread(thread);
        state.operation = operation;

        if (mapLockOwner == null || mapLockOwner.equals(thread)) {
            mapLockOwner = thread;
            waiting.remove(thread);
            state.status = "OWNS_LOCK";
            addHistory(thread, "SYNC_LOCK_ACQUIRED", operation);
            Trace.event("SYNC_LOCK_ACQUIRED",
                    thread + " acquired the single table-wide monitor for " + operation,
                    thread + " получил единый monitor всей таблицы для " + operation,
                    List.of("thread:" + thread, "mapLock", "owner:" + thread),
                    state());
            return;
        }

        waiting.add(thread);
        state.status = "BLOCKED";
        addHistory(thread, "SYNC_BLOCKED", operation);
        Trace.event("SYNC_BLOCKED",
                thread + " wants " + operation + ", but " + mapLockOwner
                        + " holds the one map monitor, so even this read must wait",
                thread + " хочет " + operation + ", но " + mapLockOwner
                        + " держит единый monitor map, поэтому даже это чтение ждёт",
                List.of("thread:" + thread, "mapLock", "owner:" + mapLockOwner, "queue:" + thread),
                state());
    }

    public void putLocked(String thread, String key, String value) {
        requireKind("SYNCHRONIZED_MAP");
        requireMapOwner(thread);
        Bin bin = bins.get(bin(key));
        bin.upsert(key, value);
        addHistory(thread, "SYNC_PUT", key + "=" + value);
        Trace.event("SYNC_PUT",
                thread + " writes " + key + "=" + value + " into bin " + bin.index
                        + " while holding the whole-map monitor",
                thread + " пишет " + key + "=" + value + " в bin " + bin.index
                        + ", удерживая monitor всей map",
                List.of("thread:" + thread, "bin:" + bin.index, "entry:" + key, "mapLock"),
                state());
    }

    public String getLocked(String thread, String key) {
        requireKind("SYNCHRONIZED_MAP");
        requireMapOwner(thread);
        Bin bin = bins.get(bin(key));
        String value = bin.value(key);
        addHistory(thread, "SYNC_GET", key + "=" + value);
        Trace.event("SYNC_GET",
                thread + " reads " + key + " from bin " + bin.index
                        + "; the read had to take the same one monitor",
                thread + " читает " + key + " из bin " + bin.index
                        + "; чтение тоже взяло тот же единый monitor",
                List.of("thread:" + thread, "bin:" + bin.index, "entry:" + key, "mapLock"),
                state());
        return value;
    }

    public void unlock(String thread) {
        requireKind("SYNCHRONIZED_MAP");
        requireMapOwner(thread);
        thread(thread).status = "DONE";
        mapLockOwner = null;
        addHistory(thread, "SYNC_LOCK_RELEASED", null);
        Trace.event("SYNC_LOCK_RELEASED",
                thread + " released the table-wide monitor; a waiting thread may now run",
                thread + " освободил monitor всей таблицы; ждущий thread может продолжить",
                List.of("thread:" + thread, "mapLock"),
                state());
    }

    // ---- ConcurrentHashMap: per-bin locks + lock-free reads ----------------

    public void lockBin(String thread, String key) {
        requireKind("CONCURRENT_HASH_MAP");
        ThreadState state = thread(thread);
        Bin bin = bins.get(bin(key));
        state.operation = "put(" + key + ")";

        if (bin.lockOwner == null || bin.lockOwner.equals(thread)) {
            bin.lockOwner = thread;
            waiting.remove(thread);
            state.status = "OWNS_LOCK";
            addHistory(thread, "CHM_BIN_LOCK_ACQUIRED", "bin " + bin.index);
            Trace.event("CHM_BIN_LOCK_ACQUIRED",
                    thread + " locked only bin " + bin.index + " for key " + key
                            + "; other bins stay free for other threads",
                    thread + " заблокировал только bin " + bin.index + " для ключа " + key
                            + "; другие bins свободны для других threads",
                    List.of("thread:" + thread, "bin:" + bin.index, "owner:" + thread),
                    state());
            return;
        }

        waiting.add(thread);
        state.status = "BLOCKED";
        addHistory(thread, "CHM_BIN_BLOCKED", "bin " + bin.index);
        Trace.event("CHM_BIN_BLOCKED",
                thread + " wants key " + key + ", which falls in bin " + bin.index
                        + " already locked by " + bin.lockOwner + ", so only this bin's writers wait",
                thread + " хочет ключ " + key + ", он попадает в bin " + bin.index
                        + ", уже занятый " + bin.lockOwner + ", поэтому ждут только писатели этого bin",
                List.of("thread:" + thread, "bin:" + bin.index, "owner:" + bin.lockOwner, "queue:" + thread),
                state());
    }

    public void putInBin(String thread, String key, String value) {
        requireKind("CONCURRENT_HASH_MAP");
        Bin bin = bins.get(bin(key));
        requireBinOwner(thread, bin);
        bin.upsert(key, value);
        addHistory(thread, "CHM_PUT", key + "=" + value);
        Trace.event("CHM_PUT",
                thread + " writes " + key + "=" + value + " into bin " + bin.index
                        + " under that bin's lock only",
                thread + " пишет " + key + "=" + value + " в bin " + bin.index
                        + " под lock только этого bin",
                List.of("thread:" + thread, "bin:" + bin.index, "entry:" + key),
                state());
    }

    public void unlockBin(String thread, String key) {
        requireKind("CONCURRENT_HASH_MAP");
        Bin bin = bins.get(bin(key));
        requireBinOwner(thread, bin);
        bin.lockOwner = null;
        thread(thread).status = "DONE";
        addHistory(thread, "CHM_BIN_LOCK_RELEASED", "bin " + bin.index);
        Trace.event("CHM_BIN_LOCK_RELEASED",
                thread + " released the lock on bin " + bin.index,
                thread + " освободил lock на bin " + bin.index,
                List.of("thread:" + thread, "bin:" + bin.index),
                state());
    }

    public String get(String thread, String key) {
        requireKind("CONCURRENT_HASH_MAP");
        ThreadState state = thread(thread);
        state.status = "RUNNING";
        state.operation = "get(" + key + ")";
        Bin bin = bins.get(bin(key));
        String value = bin.value(key);
        boolean lockedNow = bin.lockOwner != null && !bin.lockOwner.equals(thread);
        addHistory(thread, "CHM_GET", key + "=" + value);
        Trace.event("CHM_GET",
                lockedNow
                        ? thread + " reads " + key + " from bin " + bin.index
                                + " with no lock, even though " + bin.lockOwner + " is writing that bin"
                        : thread + " reads " + key + " from bin " + bin.index + " with no lock at all",
                lockedNow
                        ? thread + " читает " + key + " из bin " + bin.index
                                + " без lock, хотя " + bin.lockOwner + " пишет в этот bin"
                        : thread + " читает " + key + " из bin " + bin.index + " вообще без lock",
                List.of("thread:" + thread, "bin:" + bin.index, "entry:" + key),
                state());
        return value;
    }

    public boolean computeIfAbsent(String thread, String key, String value) {
        requireKind("CONCURRENT_HASH_MAP");
        ThreadState state = thread(thread);
        state.status = "RUNNING";
        state.operation = "computeIfAbsent(" + key + ")";
        Bin bin = bins.get(bin(key));
        String existing = bin.value(key);
        boolean inserted = existing == null;
        if (inserted) {
            bin.upsert(key, value);
        }
        addHistory(thread, "CHM_ATOMIC", key + "=" + (inserted ? value : existing));
        Trace.event("CHM_ATOMIC",
                inserted
                        ? thread + " atomically inserted missing key " + key + "=" + value
                                + " (lock the bin, check, write, unlock as one step)"
                        : thread + " saw " + key + " already present and kept " + existing
                                + " — no check-then-put race",
                inserted
                        ? thread + " атомарно вставил отсутствующий ключ " + key + "=" + value
                                + " (заблокировать bin, проверить, записать, освободить одним шагом)"
                        : thread + " увидел, что ключ " + key + " уже есть, и сохранил " + existing
                                + " — без гонки check-then-put",
                List.of("thread:" + thread, "bin:" + bin.index, "entry:" + key),
                state());
        return inserted;
    }

    public int size() {
        int total = 0;
        for (Bin bin : bins) {
            total += bin.entries.size();
        }
        return total;
    }

    // ---- internals ---------------------------------------------------------

    private void created(String descEn, String descRu) {
        Trace.event("MAP_CREATED", descEn, descRu, List.of(), state());
    }

    private ThreadState thread(String name) {
        return threads.computeIfAbsent(name, ThreadState::new);
    }

    private void requireKind(String expected) {
        if (!expected.equals(kind)) {
            throw new IllegalStateException("Operation requires " + expected + ", but this is " + kind);
        }
    }

    private void requireMapOwner(String thread) {
        if (!thread.equals(mapLockOwner)) {
            throw new IllegalStateException(thread + " must hold the map monitor first");
        }
    }

    private void requireBinOwner(String thread, Bin bin) {
        if (!thread.equals(bin.lockOwner)) {
            throw new IllegalStateException(thread + " must lock bin " + bin.index + " first");
        }
    }

    private void addHistory(String thread, String action, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("thread", thread);
        item.put("action", action);
        if (detail != null) {
            item.put("detail", detail);
        }
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("kind", kind);
        s.put("strategy", strategy);
        s.put("capacity", CAPACITY);
        s.put("mapLockOwner", mapLockOwner);
        s.put("waitingQueue", new ArrayList<>(waiting));

        List<Object> binList = new ArrayList<>();
        for (Bin bin : bins) {
            binList.add(bin.state());
        }
        s.put("bins", binList);

        List<Object> threadList = new ArrayList<>();
        for (ThreadState thread : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", thread.name);
            item.put("status", thread.status);
            if (thread.operation != null) {
                item.put("operation", thread.operation);
            }
            threadList.add(item);
        }
        s.put("threads", threadList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static final class Bin {
        final int index;
        final List<Entry> entries = new ArrayList<>();
        String lockOwner;

        Bin(int index) {
            this.index = index;
        }

        void upsert(String key, String value) {
            for (Entry entry : entries) {
                if (entry.key.equals(key)) {
                    entry.value = value;
                    return;
                }
            }
            entries.add(new Entry(key, value));
        }

        String value(String key) {
            for (Entry entry : entries) {
                if (entry.key.equals(key)) {
                    return entry.value;
                }
            }
            return null;
        }

        Map<String, Object> state() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            item.put("lockOwner", lockOwner);
            List<Object> entryList = new ArrayList<>();
            for (Entry entry : entries) {
                entryList.add(entry.state());
            }
            item.put("entries", entryList);
            return item;
        }
    }

    private static final class Entry {
        final String key;
        String value;

        Entry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        Map<String, Object> state() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("value", value);
            return item;
        }
    }

    private static final class ThreadState {
        final String name;
        String status = "READY";
        String operation;

        ThreadState(String name) {
            this.name = name;
        }
    }
}
