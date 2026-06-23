package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic teaching model for synchronized and concurrent collections.
 * It simulates named threads and iterator behavior so examples stay stable and
 * do not depend on real scheduler timing.
 */
public class VisualConcurrentCollections {

    private static final int HISTORY_LIMIT = 7;

    private final String name;
    private final String kind;
    private final String strategy;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, ThreadState> threads = new LinkedHashMap<>();
    private final Set<String> waiting = new LinkedHashSet<>();
    private final Map<String, IteratorState> iterators = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private String owner;
    private int version;

    private VisualConcurrentCollections(String name, String kind, String strategy) {
        this.name = name;
        this.kind = kind;
        this.strategy = strategy;
    }

    public static VisualConcurrentCollections synchronizedList(String name, String... values) {
        VisualConcurrentCollections collection = new VisualConcurrentCollections(
                name,
                "SYNCHRONIZED_LIST",
                "single monitor, every method takes the same lock");
        for (String value : values) {
            collection.entries.add(new Entry(String.valueOf(collection.entries.size()), value));
        }
        collection.created("Created synchronized collection '" + name
                        + "': every method uses one monitor",
                "Создана synchronized collection '" + name
                        + "': каждый метод использует один monitor");
        return collection;
    }

    public static VisualConcurrentCollections concurrentMap(String name) {
        VisualConcurrentCollections collection = new VisualConcurrentCollections(
                name,
                "CONCURRENT_HASH_MAP",
                "internal concurrency, reads do not take one global lock");
        collection.created("Created concurrent collection '" + name
                        + "': operations can progress without one global monitor",
                "Создана concurrent collection '" + name
                        + "': операции могут идти без одного общего monitor");
        return collection;
    }

    public static VisualConcurrentCollections copyOnWriteList(String name, String... values) {
        VisualConcurrentCollections collection = new VisualConcurrentCollections(
                name,
                "COPY_ON_WRITE_ARRAY_LIST",
                "read snapshot, write copies the backing array");
        for (String value : values) {
            collection.entries.add(new Entry(String.valueOf(collection.entries.size()), value));
        }
        collection.created("Created CopyOnWriteArrayList-style collection '" + name
                        + "': iterators read a stable snapshot",
                "Создана collection в стиле CopyOnWriteArrayList '" + name
                        + "': iterators читают стабильный snapshot");
        return collection;
    }

    public void beginSynchronizedOperation(String thread, String operation) {
        requireKind("SYNCHRONIZED_LIST");
        ThreadState state = thread(thread);
        state.operation = operation;

        if (owner == null) {
            owner = thread;
            waiting.remove(thread);
            state.status = "OWNS_MONITOR";
            addHistory(thread, "LOCK_ACQUIRED", operation);
            Trace.event("SYNC_LOCK_ACQUIRED",
                    thread + " acquired the single monitor to run " + operation,
                    thread + " получил единый monitor, чтобы выполнить " + operation,
                    List.of("thread:" + thread, "owner:" + thread, "lock"),
                    state());
            return;
        }

        if (owner.equals(thread)) {
            Trace.event("SYNC_LOCK_ACQUIRED",
                    thread + " already owns the monitor for " + operation,
                    thread + " уже владеет monitor для " + operation,
                    List.of("thread:" + thread, "owner:" + thread, "lock"),
                    state());
            return;
        }

        waiting.add(thread);
        state.status = "BLOCKED";
        addHistory(thread, "BLOCKED", operation);
        Trace.event("SYNC_THREAD_BLOCKED",
                thread + " wants " + operation + ", but " + owner
                        + " owns the synchronized collection monitor",
                thread + " хочет выполнить " + operation + ", но monitor synchronized collection принадлежит "
                        + owner,
                List.of("thread:" + thread, "owner:" + owner, "queue:" + thread, "lock"),
                state());
    }

    public void addToSynchronizedList(String thread, String value) {
        requireKind("SYNCHRONIZED_LIST");
        requireOwner(thread);
        Entry entry = appendListValue(value);
        addHistory(thread, "SYNC_WRITE", value);
        Trace.event("SYNC_WRITE",
                thread + " writes '" + value + "' while holding the synchronized collection monitor",
                thread + " записывает '" + value + "', удерживая monitor synchronized collection",
                List.of("thread:" + thread, "owner:" + thread, "entry:" + entry.key, "lock"),
                state());
    }

    public void addWithSynchronizedLock(String thread, String value) {
        beginSynchronizedOperation(thread, "add(" + value + ")");
        if (thread.equals(owner)) {
            addToSynchronizedList(thread, value);
            endSynchronizedOperation(thread);
        }
    }

    public void endSynchronizedOperation(String thread) {
        requireKind("SYNCHRONIZED_LIST");
        requireOwner(thread);
        thread(thread).status = "DONE";
        owner = null;
        addHistory(thread, "LOCK_RELEASED", null);
        Trace.event("SYNC_LOCK_RELEASED",
                thread + " released the synchronized collection monitor",
                thread + " освободил monitor synchronized collection",
                List.of("thread:" + thread, "lock"),
                state());
    }

    public void createFailFastIterator(String iteratorName, String thread) {
        requireKind("SYNCHRONIZED_LIST");
        IteratorState iterator = new IteratorState(iteratorName, thread, "FAIL_FAST", version, copyEntries());
        iterators.put(iteratorName, iterator);
        ThreadState state = thread(thread);
        state.status = "ITERATING";
        state.operation = "iterator()";
        addHistory(thread, "ITERATOR_CREATED", iteratorName);
        Trace.event("ITERATOR_CREATED",
                thread + " created a fail-fast iterator over the synchronized collection",
                thread + " создал fail-fast iterator по synchronized collection",
                List.of("thread:" + thread, "iterator:" + iteratorName),
                state());
    }

    public void putConcurrent(String thread, String key, String value) {
        requireKind("CONCURRENT_HASH_MAP");
        ThreadState state = thread(thread);
        state.status = "RUNNING";
        state.operation = "put(" + key + ")";
        upsertMapEntry(key, value);
        addHistory(thread, "CONCURRENT_WRITE", key + "=" + value);
        Trace.event("CONCURRENT_WRITE",
                thread + " writes key '" + key + "' without taking one global collection lock",
                thread + " записывает key '" + key + "' без одного общего lock на collection",
                List.of("thread:" + thread, "entry:" + key),
                state());
    }

    public String getConcurrent(String thread, String key) {
        requireKind("CONCURRENT_HASH_MAP");
        ThreadState state = thread(thread);
        state.status = "RUNNING";
        state.operation = "get(" + key + ")";
        Entry entry = find(key);
        String value = entry == null ? null : entry.value;
        addHistory(thread, "CONCURRENT_READ", key + "=" + value);
        Trace.event("CONCURRENT_READ",
                thread + " reads key '" + key + "' while other operations can still proceed",
                thread + " читает key '" + key + "', пока другие операции тоже могут продолжаться",
                entry == null ? List.of("thread:" + thread) : List.of("thread:" + thread, "entry:" + key),
                state());
        return value;
    }

    public boolean putIfAbsent(String thread, String key, String value) {
        requireKind("CONCURRENT_HASH_MAP");
        ThreadState state = thread(thread);
        state.status = "RUNNING";
        state.operation = "putIfAbsent(" + key + ")";
        Entry existing = find(key);
        boolean inserted = existing == null;
        if (inserted) {
            upsertMapEntry(key, value);
        }
        addHistory(thread, "PUT_IF_ABSENT", key + "=" + (inserted ? value : existing.value));
        Trace.event("ATOMIC_PUT_IF_ABSENT",
                inserted
                        ? thread + " atomically inserted missing key '" + key + "'"
                        : thread + " saw existing key '" + key + "' and kept the old value",
                inserted
                        ? thread + " атомарно вставил отсутствующий key '" + key + "'"
                        : thread + " увидел существующий key '" + key + "' и сохранил старое value",
                List.of("thread:" + thread, "entry:" + key),
                state());
        return inserted;
    }

    public void createWeakIterator(String iteratorName, String thread) {
        requireKind("CONCURRENT_HASH_MAP");
        IteratorState iterator = new IteratorState(iteratorName, thread, "WEAKLY_CONSISTENT", version, List.of());
        iterators.put(iteratorName, iterator);
        ThreadState state = thread(thread);
        state.status = "ITERATING";
        state.operation = "iterator()";
        addHistory(thread, "WEAK_ITERATOR_CREATED", iteratorName);
        Trace.event("WEAK_ITERATOR_CREATED",
                thread + " created a weakly consistent iterator that will not fail-fast",
                thread + " создал weakly consistent iterator, который не будет fail-fast",
                List.of("thread:" + thread, "iterator:" + iteratorName),
                state());
    }

    public void createSnapshotIterator(String iteratorName, String thread) {
        requireKind("COPY_ON_WRITE_ARRAY_LIST");
        IteratorState iterator = new IteratorState(iteratorName, thread, "SNAPSHOT", version, copyEntries());
        iterators.put(iteratorName, iterator);
        ThreadState state = thread(thread);
        state.status = "ITERATING";
        state.operation = "iterator()";
        addHistory(thread, "SNAPSHOT_ITERATOR_CREATED", iteratorName);
        Trace.event("SNAPSHOT_ITERATOR_CREATED",
                thread + " created an iterator over the current array snapshot",
                thread + " создал iterator по текущему array snapshot",
                List.of("thread:" + thread, "iterator:" + iteratorName),
                state());
    }

    public void addCopyOnWrite(String thread, String value) {
        requireKind("COPY_ON_WRITE_ARRAY_LIST");
        ThreadState state = thread(thread);
        state.status = "RUNNING";
        state.operation = "add(" + value + ")";
        Entry entry = appendListValue(value);
        addHistory(thread, "COPY_ON_WRITE", value);
        Trace.event("COPY_ON_WRITE_WRITE",
                thread + " copies the backing array, adds '" + value
                        + "', and publishes the new array",
                thread + " копирует backing array, добавляет '" + value
                        + "' и публикует новый array",
                List.of("thread:" + thread, "entry:" + entry.key, "array"),
                state());
    }

    public String next(String iteratorName) {
        IteratorState iterator = iterators.get(iteratorName);
        if (iterator == null) {
            throw new IllegalArgumentException("Unknown iterator: " + iteratorName);
        }
        ThreadState thread = thread(iterator.thread);
        thread.status = "ITERATING";
        thread.operation = "next(" + iteratorName + ")";

        if ("FAIL_FAST".equals(iterator.mode) && iterator.expectedVersion != version) {
            iterator.status = "INVALIDATED";
            addHistory(iterator.thread, "FAIL_FAST", iteratorName);
            Trace.event("FAIL_FAST_ITERATOR_INVALIDATED",
                    iterator.thread + " tries to continue a fail-fast iterator after another write",
                    iterator.thread + " пытается продолжить fail-fast iterator после другой записи",
                    List.of("thread:" + iterator.thread, "iterator:" + iteratorName),
                    state());
            return null;
        }

        List<Entry> visible = visibleEntries(iterator);
        if (iterator.cursor >= visible.size()) {
            iterator.status = "EXHAUSTED";
            addHistory(iterator.thread, "ITERATOR_DONE", iteratorName);
            Trace.event("ITERATOR_EXHAUSTED",
                    iterator.thread + " reached the end of iterator '" + iteratorName + "'",
                    iterator.thread + " дошёл до конца iterator '" + iteratorName + "'",
                    List.of("thread:" + iterator.thread, "iterator:" + iteratorName),
                    state());
            return null;
        }

        Entry entry = visible.get(iterator.cursor++);
        iterator.status = "READING";
        boolean changed = iterator.expectedVersion != version;
        String event = switch (iterator.mode) {
            case "WEAKLY_CONSISTENT" -> changed ? "WEAK_ITERATOR_CONTINUES" : "ITERATOR_NEXT";
            case "SNAPSHOT" -> "SNAPSHOT_ITERATOR_READ";
            default -> "ITERATOR_NEXT";
        };
        String descEn = switch (event) {
            case "WEAK_ITERATOR_CONTINUES" -> iterator.thread
                    + " continues iteration after a concurrent write; no fail-fast exception is thrown";
            case "SNAPSHOT_ITERATOR_READ" -> iterator.thread
                    + " reads '" + entry.value + "' from the old snapshot";
            default -> iterator.thread + " reads '" + entry.value + "' from iterator '" + iteratorName + "'";
        };
        String descRu = switch (event) {
            case "WEAK_ITERATOR_CONTINUES" -> iterator.thread
                    + " продолжает iteration после concurrent write; fail-fast exception не выбрасывается";
            case "SNAPSHOT_ITERATOR_READ" -> iterator.thread
                    + " читает '" + entry.value + "' из старого snapshot";
            default -> iterator.thread + " читает '" + entry.value + "' из iterator '" + iteratorName + "'";
        };
        if ("WEAKLY_CONSISTENT".equals(iterator.mode)) {
            iterator.expectedVersion = version;
        }
        addHistory(iterator.thread, event, entry.value);
        Trace.event(event,
                descEn,
                descRu,
                List.of("thread:" + iterator.thread, "iterator:" + iteratorName, "entry:" + entry.key),
                state());
        return entry.value;
    }

    public int size() {
        return entries.size();
    }

    private void created(String descEn, String descRu) {
        Trace.event("COLLECTION_CREATED", descEn, descRu, List.of(), state());
    }

    private Entry appendListValue(String value) {
        version++;
        Entry entry = new Entry(String.valueOf(entries.size()), value);
        entries.add(entry);
        return entry;
    }

    private void upsertMapEntry(String key, String value) {
        Entry existing = find(key);
        version++;
        if (existing == null) {
            entries.add(new Entry(key, value));
        } else {
            existing.value = value;
        }
    }

    private Entry find(String key) {
        for (Entry entry : entries) {
            if (entry.key.equals(key)) {
                return entry;
            }
        }
        return null;
    }

    private List<Entry> visibleEntries(IteratorState iterator) {
        if ("SNAPSHOT".equals(iterator.mode) || "FAIL_FAST".equals(iterator.mode)) {
            return iterator.snapshot;
        }
        return entries;
    }

    private ThreadState thread(String name) {
        return threads.computeIfAbsent(name, ThreadState::new);
    }

    private void requireKind(String expected) {
        if (!expected.equals(kind)) {
            throw new IllegalStateException("Operation requires " + expected + ", but this is " + kind);
        }
    }

    private void requireOwner(String thread) {
        if (!thread.equals(owner)) {
            throw new IllegalStateException(thread + " must own the synchronized collection monitor first");
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

    private List<Entry> copyEntries() {
        List<Entry> copy = new ArrayList<>();
        for (Entry entry : entries) {
            copy.add(new Entry(entry.key, entry.value));
        }
        return copy;
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("kind", kind);
        s.put("strategy", strategy);
        s.put("owner", owner);
        s.put("version", version);
        s.put("waitingQueue", new ArrayList<>(waiting));

        List<Object> entryList = new ArrayList<>();
        for (Entry entry : entries) {
            entryList.add(entry.state());
        }
        s.put("entries", entryList);

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

        List<Object> iteratorList = new ArrayList<>();
        for (IteratorState iterator : iterators.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", iterator.name);
            item.put("thread", iterator.thread);
            item.put("mode", iterator.mode);
            item.put("cursor", iterator.cursor);
            item.put("expectedVersion", iterator.expectedVersion);
            item.put("status", iterator.status);
            List<Object> snapshot = new ArrayList<>();
            for (Entry entry : iterator.snapshot) {
                snapshot.add(entry.state());
            }
            item.put("snapshot", snapshot);
            iteratorList.add(item);
        }
        s.put("iterators", iteratorList);
        s.put("history", new ArrayList<>(history));
        return s;
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

    private static final class IteratorState {
        final String name;
        final String thread;
        final String mode;
        final List<Entry> snapshot;
        int expectedVersion;
        int cursor;
        String status = "OPEN";

        IteratorState(String name, String thread, String mode, int expectedVersion, List<Entry> snapshot) {
            this.name = name;
            this.thread = thread;
            this.mode = mode;
            this.expectedVersion = expectedVersion;
            this.snapshot = new ArrayList<>(snapshot);
        }
    }
}
