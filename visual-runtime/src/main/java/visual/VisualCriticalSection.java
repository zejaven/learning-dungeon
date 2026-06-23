package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A deterministic teaching model of a critical section. It does not start real
 * Java threads; instead, examples name simulated threads so the visualization can
 * show ownership, waiting, protected reads/writes and a lost update without
 * relying on scheduler timing.
 */
public class VisualCriticalSection {

    private static final int HISTORY_LIMIT = 6;

    private final String name;
    private final Map<String, ThreadState> threads = new LinkedHashMap<>();
    private final Set<String> waiting = new LinkedHashSet<>();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Map<String, Integer> unsafeReads = new LinkedHashMap<>();

    private int sharedValue;
    private String owner;

    public VisualCriticalSection() {
        this("counter", 0);
    }

    public VisualCriticalSection(String name, int initialValue) {
        this.name = name;
        this.sharedValue = initialValue;
        Trace.event("CRITICAL_SECTION_CREATED",
                "Created critical section '" + name + "' with shared value " + initialValue,
                "Создана критическая секция '" + name + "' с общим значением " + initialValue,
                List.of(),
                state());
    }

    public void enter(String thread) {
        ThreadState state = thread(thread);
        if (owner == null) {
            owner = thread;
            waiting.remove(thread);
            state.status = "IN_SECTION";
            addHistory(thread, "ENTER", null);
            Trace.event("CRITICAL_SECTION_ENTER",
                    thread + " entered the critical section and now owns the lock",
                    thread + " вошёл в критическую секцию и теперь владеет lock",
                    List.of("thread:" + thread, "owner:" + thread),
                    state());
            return;
        }

        if (owner.equals(thread)) {
            Trace.event("CRITICAL_SECTION_ENTER",
                    thread + " is already inside the critical section",
                    thread + " уже находится внутри критической секции",
                    List.of("thread:" + thread, "owner:" + thread),
                    state());
            return;
        }

        waiting.add(thread);
        state.status = "WAITING";
        addHistory(thread, "WAIT", null);
        Trace.event("THREAD_WAITING",
                thread + " tried to enter, but " + owner + " owns the lock, so " + thread + " waits",
                thread + " попытался войти, но lock принадлежит " + owner + ", поэтому " + thread + " ждёт",
                List.of("thread:" + thread, "owner:" + owner, "queue:" + thread),
                state());
    }

    public int read(String thread) {
        requireOwner(thread);
        ThreadState state = thread(thread);
        state.lastRead = sharedValue;
        addHistory(thread, "READ", sharedValue);
        Trace.event("SHARED_READ",
                thread + " reads shared value " + sharedValue + " inside the critical section",
                thread + " читает общее значение " + sharedValue + " внутри критической секции",
                List.of("thread:" + thread, "owner:" + thread, "value"),
                state());
        return sharedValue;
    }

    public void write(String thread, int newValue) {
        requireOwner(thread);
        int oldValue = sharedValue;
        sharedValue = newValue;
        addHistory(thread, "WRITE", newValue);
        Trace.event("SHARED_WRITE",
                thread + " writes shared value " + newValue + " (was " + oldValue + ")",
                thread + " записывает общее значение " + newValue + " (было " + oldValue + ")",
                List.of("thread:" + thread, "owner:" + thread, "value"),
                state());
    }

    public void exit(String thread) {
        requireOwner(thread);
        thread(thread).status = "DONE";
        owner = null;
        addHistory(thread, "EXIT", null);
        Trace.event("CRITICAL_SECTION_EXIT",
                thread + " exits the critical section and releases the lock",
                thread + " выходит из критической секции и освобождает lock",
                List.of("thread:" + thread),
                state());

        if (!waiting.isEmpty()) {
            String next = waiting.iterator().next();
            waiting.remove(next);
            owner = next;
            thread(next).status = "IN_SECTION";
            addHistory(next, "ENTER", null);
            Trace.event("CRITICAL_SECTION_ENTER",
                    "The lock is handed to waiting thread " + next,
                    "Lock передан ожидающему thread " + next,
                    List.of("thread:" + next, "owner:" + next),
                    state());
        }
    }

    public int unsafeRead(String thread) {
        ThreadState state = thread(thread);
        state.status = "RUNNING_UNPROTECTED";
        state.lastRead = sharedValue;
        unsafeReads.put(thread, sharedValue);
        addHistory(thread, "UNSAFE_READ", sharedValue);
        Trace.event("UNSAFE_READ",
                thread + " reads " + sharedValue + " without owning the lock",
                thread + " читает " + sharedValue + " без владения lock",
                List.of("thread:" + thread, "value"),
                state());
        return sharedValue;
    }

    public void unsafeWrite(String thread, int newValue) {
        ThreadState state = thread(thread);
        state.status = "RUNNING_UNPROTECTED";
        Integer readValue = unsafeReads.get(thread);
        int oldValue = sharedValue;
        sharedValue = newValue;
        boolean staleWrite = readValue != null && readValue < oldValue && newValue <= oldValue;
        addHistory(thread, staleWrite ? "LOST_UPDATE" : "UNSAFE_WRITE", newValue);

        if (staleWrite) {
            Trace.event("LOST_UPDATE",
                    "Lost update: " + thread + " writes " + newValue
                            + " from stale read " + readValue + " while shared value was already " + oldValue,
                    "Потерянное обновление: " + thread + " записывает " + newValue
                            + " из устаревшего чтения " + readValue + ", хотя общее значение уже было " + oldValue,
                    List.of("thread:" + thread, "value"),
                    state());
        } else {
            Trace.event("UNSAFE_WRITE",
                    thread + " writes " + newValue + " without owning the lock",
                    thread + " записывает " + newValue + " без владения lock",
                    List.of("thread:" + thread, "value"),
                    state());
        }
    }

    public void outsideWork(String thread) {
        ThreadState state = thread(thread);
        state.status = "OUTSIDE_WORK";
        addHistory(thread, "OUTSIDE_WORK", null);
        Trace.event("OUTSIDE_WORK",
                thread + " does work that does not touch the shared value, so no lock is needed",
                thread + " выполняет работу без доступа к общему значению, поэтому lock не нужен",
                List.of("thread:" + thread),
                state());
    }

    public int value() {
        return sharedValue;
    }

    private void requireOwner(String thread) {
        if (!thread.equals(owner)) {
            throw new IllegalStateException(thread + " must enter the critical section before touching " + name);
        }
    }

    private ThreadState thread(String name) {
        return threads.computeIfAbsent(name, ThreadState::new);
    }

    private void addHistory(String thread, String action, Integer value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("thread", thread);
        item.put("action", action);
        if (value != null) {
            item.put("value", value);
        }
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("sharedValue", sharedValue);
        s.put("owner", owner);
        s.put("waitingQueue", new ArrayList<>(waiting));

        List<Object> threadList = new ArrayList<>();
        for (ThreadState t : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", t.name);
            item.put("status", t.status);
            if (t.lastRead != null) {
                item.put("lastRead", t.lastRead);
            }
            threadList.add(item);
        }
        s.put("threads", threadList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static final class ThreadState {
        final String name;
        String status = "READY";
        Integer lastRead;

        ThreadState(String name) {
            this.name = name;
        }
    }
}
