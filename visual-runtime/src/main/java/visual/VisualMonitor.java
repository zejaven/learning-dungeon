package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model of a Java object monitor used by synchronized,
 * wait(), notify() and notifyAll(). It does not start real Java threads; instead,
 * examples name simulated threads so the visualization can show the monitor
 * owner, wait set, entry set and re-acquire step without relying on scheduler
 * timing.
 */
public class VisualMonitor {

    private static final int HISTORY_LIMIT = 8;

    private final String name;
    private final Map<String, ThreadState> threads = new LinkedHashMap<>();
    private final List<Waiter> waitSet = new ArrayList<>();
    private final List<Entry> entrySet = new ArrayList<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private String owner;
    private boolean conditionReady;

    public VisualMonitor() {
        this("monitor", false);
    }

    public VisualMonitor(String name) {
        this(name, false);
    }

    public VisualMonitor(String name, boolean initialConditionReady) {
        this.name = Objects.requireNonNull(name, "name");
        this.conditionReady = initialConditionReady;
        addHistory(name, "CREATE", null);
        Trace.event("MONITOR_CREATED",
                "Created monitor '" + name + "'; no thread owns it yet",
                "Создан monitor '" + name + "'; пока ни один thread им не владеет",
                List.of("monitor"),
                state());
    }

    public void enter(String thread) {
        ThreadState state = thread(thread);
        if (owner == null) {
            owner = thread;
            state.status = "IN_SYNCHRONIZED";
            addHistory(thread, "ENTER", null);
            Trace.event("MONITOR_ENTER",
                    thread + " entered the synchronized block and became the monitor owner",
                    thread + " вошёл в synchronized-блок и стал владельцем monitor",
                    List.of("thread:" + thread, "owner:" + thread, "monitor"),
                    state());
            return;
        }

        if (owner.equals(thread)) {
            addHistory(thread, "REENTER", null);
            Trace.event("MONITOR_ENTER",
                    thread + " is already the monitor owner; synchronized is reentrant",
                    thread + " уже владеет monitor; synchronized реентерабелен",
                    List.of("thread:" + thread, "owner:" + thread, "monitor"),
                    state());
            return;
        }

        addEntry(thread, "enter");
        state.status = "BLOCKED";
        addHistory(thread, "BLOCKED", "owner=" + owner);
        Trace.event("MONITOR_BLOCKED",
                thread + " tried to enter synchronized, but " + owner
                        + " owns the monitor, so " + thread + " waits in the entry set",
                thread + " попытался войти в synchronized, но monitor принадлежит " + owner
                        + ", поэтому " + thread + " ждёт в entry set",
                List.of("thread:" + thread, "owner:" + owner, "entry:" + thread),
                state());
    }

    public boolean checkCondition(String thread) {
        requireOwner(thread, "check the condition");
        ThreadState state = thread(thread);
        state.lastCheck = conditionReady;
        String action = conditionReady ? "CHECK_TRUE" : "CHECK_FALSE";
        addHistory(thread, action, null);
        Trace.event(conditionReady ? "CONDITION_CHECK_TRUE" : "CONDITION_CHECK_FALSE",
                thread + " checks the guard condition inside synchronized: " + conditionReady,
                thread + " проверяет guard condition внутри synchronized: " + conditionReady,
                List.of("thread:" + thread, "owner:" + thread, "condition"),
                state());
        return conditionReady;
    }

    public void setConditionReady(String thread, boolean ready) {
        requireOwner(thread, "change the condition");
        conditionReady = ready;
        addHistory(thread, ready ? "CONDITION_TRUE" : "CONDITION_FALSE", null);
        Trace.event("CONDITION_UPDATED",
                thread + " updates the shared condition to " + ready
                        + " while still owning the monitor",
                thread + " меняет shared condition на " + ready
                        + ", всё ещё владея monitor",
                List.of("thread:" + thread, "owner:" + thread, "condition"),
                state());
    }

    public void waitOnCondition(String thread) {
        requireOwner(thread, "call wait()");
        owner = null;
        ThreadState state = thread(thread);
        state.status = "WAITING";
        waitSet.add(new Waiter(thread));
        addHistory(thread, "WAIT", null);
        Trace.event("MONITOR_WAIT_RELEASED",
                thread + " called wait(): it entered the wait set and released the monitor",
                thread + " вызвал wait(): он попал в wait set и освободил monitor",
                List.of("thread:" + thread, "wait:" + thread, "monitor"),
                state());
        grantNextEntry();
    }

    public void notifyOne(String thread) {
        requireOwner(thread, "call notify()");
        if (waitSet.isEmpty()) {
            addHistory(thread, "NOTIFY_NONE", null);
            Trace.event("MONITOR_NOTIFY_NO_WAITER",
                    thread + " called notify(), but the wait set was empty; the signal is not stored",
                    thread + " вызвал notify(), но wait set был пуст; сигнал не сохраняется",
                    List.of("thread:" + thread, "owner:" + thread, "wait-set"),
                    state());
            return;
        }

        Waiter waiter = waitSet.remove(0);
        addEntry(waiter.thread, "notified");
        thread(waiter.thread).status = "BLOCKED_AFTER_NOTIFY";
        addHistory(thread, "NOTIFY", waiter.thread);
        Trace.event("MONITOR_NOTIFY",
                thread + " called notify(): " + waiter.thread
                        + " left the wait set, but cannot continue until the monitor is released",
                thread + " вызвал notify(): " + waiter.thread
                        + " вышел из wait set, но продолжит только после освобождения monitor",
                List.of("thread:" + thread, "owner:" + thread, "entry:" + waiter.thread),
                state());
    }

    public void notifyAllWaiters(String thread) {
        requireOwner(thread, "call notifyAll()");
        int count = waitSet.size();
        List<String> highlights = new ArrayList<>();
        highlights.add("thread:" + thread);
        highlights.add("owner:" + thread);
        highlights.add("wait-set");

        while (!waitSet.isEmpty()) {
            Waiter waiter = waitSet.remove(0);
            addEntry(waiter.thread, "notified");
            thread(waiter.thread).status = "BLOCKED_AFTER_NOTIFY";
            highlights.add("entry:" + waiter.thread);
        }

        addHistory(thread, "NOTIFY_ALL", String.valueOf(count));
        Trace.event("MONITOR_NOTIFY_ALL",
                thread + " called notifyAll(): " + count
                        + " waiting thread(s) moved toward monitor re-acquisition",
                thread + " вызвал notifyAll(): " + count
                        + " waiting thread(s) перешли к повторному захвату monitor",
                highlights,
                state());
    }

    public void spuriousWakeup(String thread) {
        int index = indexOfWaiter(thread);
        if (index < 0) {
            throw new IllegalStateException(thread + " is not in the wait set");
        }

        waitSet.remove(index);
        addEntry(thread, "spurious");
        thread(thread).status = "BLOCKED_AFTER_SPURIOUS";
        addHistory(thread, "SPURIOUS_WAKEUP", null);
        Trace.event("MONITOR_SPURIOUS_WAKEUP",
                thread + " woke without notify(); it still must re-acquire the monitor and re-check the condition",
                thread + " проснулся без notify(); ему всё равно нужно заново захватить monitor и проверить condition",
                List.of("thread:" + thread, "entry:" + thread, "condition"),
                state());
        grantNextEntry();
    }

    public void exit(String thread) {
        requireOwner(thread, "exit synchronized");
        owner = null;
        thread(thread).status = "OUTSIDE";
        addHistory(thread, "EXIT", null);
        Trace.event("MONITOR_EXIT",
                thread + " exits synchronized and releases the monitor",
                thread + " выходит из synchronized и освобождает monitor",
                List.of("thread:" + thread, "monitor"),
                state());
        grantNextEntry();
    }

    public boolean conditionReady() {
        return conditionReady;
    }

    private void grantNextEntry() {
        if (owner != null || entrySet.isEmpty()) {
            return;
        }

        Entry next = entrySet.remove(0);
        owner = next.thread;
        ThreadState state = thread(next.thread);
        boolean afterWait = "notified".equals(next.reason) || "spurious".equals(next.reason);
        state.status = afterWait ? "REACQUIRED_AFTER_WAIT" : "IN_SYNCHRONIZED";
        addHistory(next.thread, afterWait ? "REACQUIRE" : "ENTER_FROM_ENTRY_SET", null);

        if (afterWait) {
            Trace.event("MONITOR_REACQUIRED",
                    next.thread + " re-acquired the monitor; now wait() can return",
                    next.thread + " заново захватил monitor; теперь wait() может вернуться",
                    List.of("thread:" + next.thread, "owner:" + next.thread, "monitor"),
                    state());
        } else {
            Trace.event("MONITOR_ENTRY_GRANTED",
                    next.thread + " moved from the entry set into synchronized",
                    next.thread + " перешёл из entry set в synchronized",
                    List.of("thread:" + next.thread, "owner:" + next.thread, "monitor"),
                    state());
        }
    }

    private void addEntry(String thread, String reason) {
        for (Entry entry : entrySet) {
            if (entry.thread.equals(thread)) {
                entry.reason = reason;
                return;
            }
        }
        entrySet.add(new Entry(thread, reason));
    }

    private int indexOfWaiter(String thread) {
        for (int i = 0; i < waitSet.size(); i++) {
            if (waitSet.get(i).thread.equals(thread)) {
                return i;
            }
        }
        return -1;
    }

    private void requireOwner(String thread, String action) {
        if (!thread.equals(owner)) {
            throw new IllegalStateException(thread + " must own monitor '" + name + "' to " + action);
        }
    }

    private ThreadState thread(String name) {
        return threads.computeIfAbsent(name, ThreadState::new);
    }

    private void addHistory(String actor, String action, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
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
        s.put("conditionReady", conditionReady);
        s.put("owner", owner);
        s.put("monitorState", owner == null ? "FREE" : "OWNED");

        List<Object> waiters = new ArrayList<>();
        for (Waiter waiter : waitSet) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("thread", waiter.thread);
            waiters.add(item);
        }
        s.put("waitSet", waiters);

        List<Object> entrants = new ArrayList<>();
        for (Entry entry : entrySet) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("thread", entry.thread);
            item.put("reason", entry.reason);
            entrants.add(item);
        }
        s.put("entrySet", entrants);

        List<Object> threadList = new ArrayList<>();
        for (ThreadState t : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", t.name);
            item.put("status", t.status);
            if (t.lastCheck != null) {
                item.put("lastCheck", t.lastCheck);
            }
            threadList.add(item);
        }
        s.put("threads", threadList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static final class Waiter {
        final String thread;

        Waiter(String thread) {
            this.thread = thread;
        }
    }

    private static final class Entry {
        final String thread;
        String reason;

        Entry(String thread, String reason) {
            this.thread = thread;
            this.reason = reason;
        }
    }

    private static final class ThreadState {
        final String name;
        String status = "OUTSIDE";
        Boolean lastCheck;

        ThreadState(String name) {
            this.name = name;
        }
    }
}
