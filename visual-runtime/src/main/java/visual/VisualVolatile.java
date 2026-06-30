package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for Java volatile visibility and ordering.
 * It simulates named threads, a plain data field, a ready flag, and a volatile
 * counter so examples can show Java Memory Model ideas without depending on
 * scheduler timing.
 */
public class VisualVolatile {

    private static final int HISTORY_LIMIT = 8;

    private final String name;
    private final boolean readyVolatile;
    private final Map<String, ThreadView> threads = new LinkedHashMap<>();
    private final Map<String, Integer> counterReads = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private int data;
    private boolean ready;
    private int counter;
    private String lastVolatileWriter;
    private String lastHappensBefore;

    public VisualVolatile() {
        this("volatile-scene", true);
    }

    public VisualVolatile(String name) {
        this(name, true);
    }

    public VisualVolatile(String name, boolean readyVolatile) {
        this.name = Objects.requireNonNull(name, "name");
        this.readyVolatile = readyVolatile;
        Trace.event("VOLATILE_MODEL_CREATED",
                "Created scene '" + name + "' with ready as "
                        + (readyVolatile ? "volatile" : "plain") + " boolean",
                "Создана сцена '" + name + "', где ready — "
                        + (readyVolatile ? "volatile" : "обычный") + " boolean",
                List.of(),
                state());
    }

    public void writeData(String thread, int value) {
        ThreadView view = thread(thread);
        data = value;
        view.dataView = value;
        view.status = "WROTE_DATA";
        addHistory(thread, "WRITE_DATA", "data", value);
        Trace.event("PLAIN_DATA_WRITE",
                thread + " writes plain data = " + value + ". Other threads are not forced to refresh yet.",
                thread + " записывает обычное data = " + value + ". Другие threads пока не обязаны обновлять свой вид.",
                List.of("thread:" + thread, "memory:data"),
                state());
    }

    public int readData(String thread) {
        ThreadView view = thread(thread);
        view.status = "READ_DATA";
        boolean stale = view.dataView != data;
        addHistory(thread, stale ? "READ_DATA_STALE" : "READ_DATA", "data", view.dataView);
        Trace.event(stale ? "PLAIN_DATA_READ_STALE" : "PLAIN_DATA_READ",
                stale
                        ? thread + " reads local data = " + view.dataView
                        + " while main memory already has " + data
                        : thread + " reads data = " + view.dataView + " from its refreshed view",
                stale
                        ? thread + " читает локальное data = " + view.dataView
                        + ", хотя в main memory уже " + data
                        : thread + " читает data = " + view.dataView + " из обновлённого вида",
                List.of("thread:" + thread, "memory:data"),
                state());
        return view.dataView;
    }

    public void writeReady(String thread, boolean value) {
        ThreadView view = thread(thread);
        ready = value;
        view.readyView = value;
        view.status = readyVolatile ? "VOLATILE_WRITE" : "PLAIN_WRITE";
        addHistory(thread, readyVolatile ? "VOLATILE_WRITE_READY" : "PLAIN_WRITE_READY", "ready", value);

        if (readyVolatile) {
            lastVolatileWriter = thread;
            Trace.event("VOLATILE_WRITE",
                    thread + " writes volatile ready = " + value
                            + ". This release-publishes earlier plain writes.",
                    thread + " записывает volatile ready = " + value
                            + ". Эта release-запись публикует предыдущие обычные записи.",
                    List.of("thread:" + thread, "memory:ready"),
                    state());
            return;
        }

        Trace.event("PLAIN_FLAG_WRITE",
                thread + " writes plain ready = " + value
                        + ". Readers may keep an older local value.",
                thread + " записывает обычное ready = " + value
                        + ". Readers могут сохранить старое локальное значение.",
                List.of("thread:" + thread, "memory:ready"),
                state());
    }

    public boolean readReady(String thread) {
        ThreadView view = thread(thread);
        if (readyVolatile) {
            view.readyView = ready;
            view.dataView = data;
            view.counterView = counter;
            view.status = "VOLATILE_READ";
            addHistory(thread, "VOLATILE_READ_READY", "ready", ready);
            Trace.event("VOLATILE_READ",
                    thread + " reads volatile ready = " + ready
                            + " and refreshes writes published before it.",
                    thread + " читает volatile ready = " + ready
                            + " и обновляет записи, опубликованные перед ним.",
                    List.of("thread:" + thread, "memory:ready"),
                    state());

            if (ready && lastVolatileWriter != null) {
                lastHappensBefore = lastVolatileWriter + " volatile write -> " + thread + " volatile read";
                Trace.event("HAPPENS_BEFORE_ESTABLISHED",
                        "The volatile write by " + lastVolatileWriter
                                + " happens-before this volatile read by " + thread,
                        "Volatile-запись от " + lastVolatileWriter
                                + " happens-before этому volatile-чтению от " + thread,
                        List.of("thread:" + thread, "memory:ready", "memory:data"),
                        state());
            }
            return view.readyView;
        }

        view.status = "PLAIN_READ";
        boolean stale = view.readyView != ready;
        addHistory(thread, stale ? "PLAIN_READ_READY_STALE" : "PLAIN_READ_READY", "ready", view.readyView);
        Trace.event(stale ? "PLAIN_READ_STALE" : "PLAIN_FLAG_READ",
                stale
                        ? thread + " reads local ready = " + view.readyView
                        + " while main memory already has " + ready
                        : thread + " reads plain ready = " + view.readyView,
                stale
                        ? thread + " читает локальное ready = " + view.readyView
                        + ", хотя в main memory уже " + ready
                        : thread + " читает обычное ready = " + view.readyView,
                List.of("thread:" + thread, "memory:ready"),
                state());
        return view.readyView;
    }

    public void refreshPlain(String thread) {
        ThreadView view = thread(thread);
        view.readyView = ready;
        view.dataView = data;
        view.counterView = counter;
        view.status = "REFRESHED";
        addHistory(thread, "REFRESH_PLAIN", "local view", "main memory");
        Trace.event("EVENTUAL_VISIBILITY",
                thread + " eventually refreshes its local view, but plain fields give no timing guarantee.",
                thread + " со временем обновляет локальный вид, но обычные fields не дают гарантии по времени.",
                List.of("thread:" + thread, "memory:ready", "memory:data"),
                state());
    }

    public int readCounter(String thread) {
        ThreadView view = thread(thread);
        view.counterView = counter;
        view.status = "VOLATILE_COUNTER_READ";
        counterReads.put(thread, counter);
        addHistory(thread, "VOLATILE_COUNTER_READ", "counter", counter);
        Trace.event("VOLATILE_COUNTER_READ",
                thread + " performs a volatile read of counter = " + counter,
                thread + " выполняет volatile-чтение counter = " + counter,
                List.of("thread:" + thread, "memory:counter"),
                state());
        return counter;
    }

    public void writeCounter(String thread, int value) {
        ThreadView view = thread(thread);
        Integer readValue = counterReads.get(thread);
        int oldValue = counter;
        counter = value;
        view.counterView = value;
        view.status = "VOLATILE_COUNTER_WRITE";

        boolean lostUpdate = readValue != null && readValue < oldValue && value <= oldValue;
        addHistory(thread, lostUpdate ? "LOST_UPDATE" : "VOLATILE_COUNTER_WRITE", "counter", value);

        if (lostUpdate) {
            Trace.event("LOST_UPDATE",
                    "Lost update: " + thread + " writes counter = " + value
                            + " from stale read " + readValue
                            + " while main memory was already " + oldValue,
                    "Потерянное обновление: " + thread + " записывает counter = " + value
                            + " из устаревшего чтения " + readValue
                            + ", хотя в main memory уже было " + oldValue,
                    List.of("thread:" + thread, "memory:counter"),
                    state());
            return;
        }

        Trace.event("VOLATILE_COUNTER_WRITE",
                thread + " performs a volatile write of counter = " + value,
                thread + " выполняет volatile-запись counter = " + value,
                List.of("thread:" + thread, "memory:counter"),
                state());
    }

    public int data() {
        return data;
    }

    public boolean ready() {
        return ready;
    }

    public int counter() {
        return counter;
    }

    private ThreadView thread(String name) {
        Objects.requireNonNull(name, "name");
        return threads.computeIfAbsent(name, ThreadView::new);
    }

    private void addHistory(String thread, String action, String target, Object value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("thread", thread);
        item.put("action", action);
        item.put("target", target);
        item.put("value", value);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("readyVolatile", readyVolatile);
        s.put("lastHappensBefore", lastHappensBefore);

        List<Object> variables = new ArrayList<>();
        variables.add(variable("data", "PLAIN_INT", data));
        variables.add(variable("ready", readyVolatile ? "VOLATILE_BOOLEAN" : "PLAIN_BOOLEAN", ready));
        variables.add(variable("counter", "VOLATILE_INT", counter));
        s.put("variables", variables);

        List<Object> threadList = new ArrayList<>();
        for (ThreadView view : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", view.name);
            item.put("status", view.status);
            item.put("dataView", view.dataView);
            item.put("readyView", view.readyView);
            item.put("counterView", view.counterView);
            threadList.add(item);
        }
        s.put("threads", threadList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static Map<String, Object> variable(String name, String kind, Object value) {
        Map<String, Object> variable = new LinkedHashMap<>();
        variable.put("name", name);
        variable.put("kind", kind);
        variable.put("value", value);
        return variable;
    }

    private static final class ThreadView {
        final String name;
        String status = "READY";
        int dataView;
        boolean readyView;
        int counterView;

        ThreadView(String name) {
            this.name = name;
        }
    }
}
