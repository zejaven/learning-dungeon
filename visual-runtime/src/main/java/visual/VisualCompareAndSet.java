package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic teaching model for compare-and-set. Examples use named simulated
 * threads so the trace can show stale reads, successful atomic replacement,
 * failed replacement and ABA risk without depending on scheduler timing.
 */
public class VisualCompareAndSet {

    private static final int HISTORY_LIMIT = 8;

    private final String name;
    private final Map<String, ThreadState> threads = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private int value;
    private int revision;

    public VisualCompareAndSet() {
        this("slot", 0);
    }

    public VisualCompareAndSet(String name, int initialValue) {
        this.name = name;
        this.value = initialValue;
        this.revision = 0;
        addHistory("system", "CREATE", initialValue, null, null, initialValue, null, revision);
        Trace.event("CAS_CREATED",
                "Created atomic slot '" + name + "' with value " + initialValue,
                "Создан atomic slot '" + name + "' со значением " + initialValue,
                List.of(),
                state());
    }

    public synchronized int read(String thread) {
        ThreadState state = thread(thread);
        state.status = "OBSERVED";
        state.lastRead = value;
        state.lastReadRevision = revision;
        state.lastExpected = null;
        state.lastUpdate = null;
        state.lastResult = null;
        addHistory(thread, "READ", value, null, null, value, null, revision);
        Trace.event("CAS_READ",
                thread + " reads " + value + " and can use it as the expected value",
                thread + " читает " + value + " и может использовать его как expected value",
                List.of("thread:" + thread, "slot"),
                state());
        return value;
    }

    public synchronized int retryRead(String thread) {
        ThreadState state = thread(thread);
        state.status = "RETRYING";
        state.lastRead = value;
        state.lastReadRevision = revision;
        state.lastExpected = null;
        state.lastUpdate = null;
        state.lastResult = null;
        addHistory(thread, "RETRY_READ", value, null, null, value, null, revision);
        Trace.event("CAS_RETRY",
                thread + " retries after a failed CAS and reads the fresh value " + value,
                thread + " повторяет попытку после неудачного CAS и читает свежее значение " + value,
                List.of("thread:" + thread, "slot"),
                state());
        return value;
    }

    public synchronized boolean compareAndSet(String thread, int expected, int update) {
        ThreadState state = thread(thread);
        state.status = "ATTEMPT";
        state.lastExpected = expected;
        state.lastUpdate = update;
        state.attempts++;

        int before = value;
        int beforeRevision = revision;
        boolean success = before == expected;
        if (!success) {
            state.status = "FAILED";
            state.lastResult = "FAILURE";
            addHistory(thread, "CAS_FAILURE", before, expected, update, before, false, revision);
            Trace.event("CAS_FAILURE",
                    thread + " expected " + expected + ", but the slot still contains " + before + ", so nothing changes",
                    thread + " ожидал " + expected + ", но slot содержит " + before + ", поэтому ничего не меняется",
                    List.of("thread:" + thread, "slot", "failure"),
                    state());
            return false;
        }

        boolean staleObservation = state.lastRead != null
                && state.lastRead == expected
                && state.lastReadRevision != null
                && state.lastReadRevision < beforeRevision;
        value = update;
        revision++;
        state.status = staleObservation ? "ABA_RISK" : "SUCCESS";
        state.lastResult = "SUCCESS";
        addHistory(thread, staleObservation ? "ABA_RISK" : "CAS_SUCCESS",
                before, expected, update, value, true, revision);

        if (staleObservation) {
            Trace.event("CAS_ABA_RISK",
                    thread + " CAS succeeds because the value is " + expected
                            + ", but it changed away and back since the last read",
                    thread + " CAS успешен, потому что значение снова " + expected
                            + ", но оно менялось туда и обратно после последнего read",
                    List.of("thread:" + thread, "slot", "success", "aba"),
                    state());
        } else {
            Trace.event("CAS_SUCCESS",
                    thread + " atomically replaces " + expected + " with " + update,
                    thread + " атомарно заменяет " + expected + " на " + update,
                    List.of("thread:" + thread, "slot", "success"),
                    state());
        }
        return true;
    }

    public synchronized int value() {
        return value;
    }

    public synchronized int revision() {
        return revision;
    }

    private ThreadState thread(String name) {
        return threads.computeIfAbsent(name, ThreadState::new);
    }

    private void addHistory(String thread, String action, int before, Integer expected,
                            Integer update, int after, Boolean success, int revision) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("thread", thread);
        item.put("action", action);
        item.put("before", before);
        if (expected != null) {
            item.put("expected", expected);
        }
        if (update != null) {
            item.put("update", update);
        }
        item.put("after", after);
        if (success != null) {
            item.put("result", success ? "SUCCESS" : "FAILURE");
        }
        item.put("revision", revision);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("value", value);
        s.put("revision", revision);

        List<Object> threadList = new ArrayList<>();
        for (ThreadState thread : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", thread.name);
            item.put("status", thread.status);
            item.put("attempts", thread.attempts);
            if (thread.lastRead != null) {
                item.put("lastRead", thread.lastRead);
            }
            if (thread.lastReadRevision != null) {
                item.put("lastReadRevision", thread.lastReadRevision);
            }
            if (thread.lastExpected != null) {
                item.put("lastExpected", thread.lastExpected);
            }
            if (thread.lastUpdate != null) {
                item.put("lastUpdate", thread.lastUpdate);
            }
            if (thread.lastResult != null) {
                item.put("lastResult", thread.lastResult);
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
        Integer lastReadRevision;
        Integer lastExpected;
        Integer lastUpdate;
        String lastResult;
        int attempts;

        ThreadState(String name) {
            this.name = name;
        }
    }
}
