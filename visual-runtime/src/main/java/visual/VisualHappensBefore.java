package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic teaching model for Java happens-before relationships.
 *
 * <p>The model does not start real threads. It records named thread actions,
 * shared variables, and synchronization edges so examples can show Java Memory
 * Model guarantees without depending on scheduler timing.
 */
public class VisualHappensBefore {

    private static final String UNSET = "unset";

    private final String name;
    private final Map<String, Variable> variables = new LinkedHashMap<>();
    private final Map<String, ThreadView> threads = new LinkedHashMap<>();
    private final Map<String, MonitorState> monitors = new LinkedHashMap<>();
    private final Map<String, String> lastVolatileWriteAction = new LinkedHashMap<>();
    private final Map<String, String> lastVolatileWriter = new LinkedHashMap<>();
    private final Map<String, String> terminatedActionByThread = new LinkedHashMap<>();
    private final List<Map<String, Object>> actions = new ArrayList<>();
    private final List<Map<String, Object>> edges = new ArrayList<>();

    private int nextActionId = 1;

    public VisualHappensBefore() {
        this("happens-before-scene");
    }

    public VisualHappensBefore(String name) {
        this.name = Objects.requireNonNull(name, "name");
        Trace.event("HB_MODEL_CREATED",
                "Created happens-before scene '" + name + "'",
                "Создана сцена happens-before '" + name + "'",
                List.of(),
                state());
    }

    public void writePlain(String thread, String variableName, Object value) {
        Variable variable = variable(variableName, "PLAIN");
        variable.value = value;
        variable.lastWriter = thread;

        ThreadView view = thread(thread);
        view.localValues.put(variableName, value);
        view.status = "WROTE_PLAIN";

        String actionId = action(thread, "PLAIN_WRITE", variableName, value);
        variable.lastWriteAction = actionId;

        Trace.event("PLAIN_WRITE",
                thread + " writes plain " + variableName + " = " + show(value)
                        + ". Other threads are not forced to see it yet.",
                thread + " записывает обычную " + variableName + " = " + show(value)
                        + ". Другие threads пока не обязаны это видеть.",
                List.of("thread:" + thread, "variable:" + variableName, "action:" + actionId),
                state());
    }

    public Object readPlain(String thread, String variableName) {
        Variable variable = variable(variableName, "PLAIN");
        ThreadView view = thread(thread);
        Object localValue = view.localValues.getOrDefault(variableName, UNSET);
        String actionId = action(thread, "PLAIN_READ", variableName, localValue);

        if (!Objects.equals(localValue, variable.value)) {
            view.status = "READ_STALE";
            Trace.event("PLAIN_READ_STALE",
                    thread + " reads local " + variableName + " = " + show(localValue)
                            + " while shared memory has " + show(variable.value)
                            + ". There is no happens-before edge.",
                    thread + " читает локальную " + variableName + " = " + show(localValue)
                            + ", хотя в shared memory уже " + show(variable.value)
                            + ". Нет happens-before связи.",
                    List.of("thread:" + thread, "variable:" + variableName, "action:" + actionId),
                    state());
            return localValue;
        }

        view.status = "READ_VISIBLE";
        boolean fromOtherThread = variable.lastWriter != null && !variable.lastWriter.equals(thread);
        if (fromOtherThread && view.lastRefreshKind != null) {
            Trace.event("TRANSITIVE_VISIBILITY",
                    thread + " reads " + variableName + " = " + show(localValue)
                            + " because the earlier write became visible through "
                            + view.lastRefreshKind,
                    thread + " читает " + variableName + " = " + show(localValue)
                            + ", потому что более ранняя запись стала видимой через "
                            + view.lastRefreshKind,
                    List.of("thread:" + thread, "variable:" + variableName, "action:" + actionId),
                    state());
            return localValue;
        }

        Trace.event("PLAIN_READ",
                thread + " reads visible " + variableName + " = " + show(localValue),
                thread + " читает видимую " + variableName + " = " + show(localValue),
                List.of("thread:" + thread, "variable:" + variableName, "action:" + actionId),
                state());
        return localValue;
    }

    public void writeVolatile(String thread, String variableName, Object value) {
        Variable variable = variable(variableName, "VOLATILE");
        variable.kind = "VOLATILE";
        variable.value = value;
        variable.lastWriter = thread;

        ThreadView view = thread(thread);
        view.localValues.put(variableName, value);
        view.status = "VOLATILE_WRITE";

        String actionId = action(thread, "VOLATILE_WRITE", variableName, value);
        variable.lastWriteAction = actionId;
        lastVolatileWriteAction.put(variableName, actionId);
        lastVolatileWriter.put(variableName, thread);

        Trace.event("VOLATILE_WRITE",
                thread + " writes volatile " + variableName + " = " + show(value)
                        + ". Earlier actions in this thread can be released.",
                thread + " записывает volatile " + variableName + " = " + show(value)
                        + ". Более ранние действия этого thread могут быть опубликованы.",
                List.of("thread:" + thread, "variable:" + variableName, "action:" + actionId),
                state());
    }

    public Object readVolatile(String thread, String variableName) {
        Variable variable = variable(variableName, "VOLATILE");
        variable.kind = "VOLATILE";
        ThreadView view = thread(thread);
        view.localValues.put(variableName, variable.value);
        view.status = "VOLATILE_READ";

        String actionId = action(thread, "VOLATILE_READ", variableName, variable.value);
        String priorWrite = lastVolatileWriteAction.get(variableName);
        if (priorWrite != null) {
            String writer = lastVolatileWriter.get(variableName);
            addEdge(priorWrite, actionId, "VOLATILE", variableName);
            refreshThread(view, "VOLATILE", writer);
            Trace.event("VOLATILE_HAPPENS_BEFORE",
                    thread + " reads volatile " + variableName + " = " + show(variable.value)
                            + ". The prior volatile write by " + writer
                            + " happens-before this read.",
                    thread + " читает volatile " + variableName + " = " + show(variable.value)
                            + ". Предыдущая volatile-запись от " + writer
                            + " happens-before этому чтению.",
                    List.of("thread:" + thread, "variable:" + variableName, "action:" + actionId),
                    state());
            return variable.value;
        }

        Trace.event("VOLATILE_READ",
                thread + " reads volatile " + variableName + " = " + show(variable.value),
                thread + " читает volatile " + variableName + " = " + show(variable.value),
                List.of("thread:" + thread, "variable:" + variableName, "action:" + actionId),
                state());
        return variable.value;
    }

    public void lock(String thread, String monitorName) {
        MonitorState monitor = monitors.computeIfAbsent(monitorName, MonitorState::new);
        if (monitor.owner != null && !monitor.owner.equals(thread)) {
            throw new IllegalStateException(monitorName + " is already owned by " + monitor.owner);
        }

        ThreadView view = thread(thread);
        monitor.owner = thread;
        view.status = "LOCKED";

        String actionId = action(thread, "MONITOR_ACQUIRE", monitorName, null);
        if (monitor.lastReleaseAction != null) {
            addEdge(monitor.lastReleaseAction, actionId, "MONITOR", monitorName);
            refreshThread(view, "MONITOR", monitor.lastReleaseThread);
            Trace.event("MONITOR_HAPPENS_BEFORE",
                    thread + " locks " + monitorName + ". The previous unlock by "
                            + monitor.lastReleaseThread + " happens-before this lock.",
                    thread + " входит в lock " + monitorName + ". Предыдущий unlock от "
                            + monitor.lastReleaseThread + " happens-before этому lock.",
                    List.of("thread:" + thread, "monitor:" + monitorName, "action:" + actionId),
                    state());
            return;
        }

        Trace.event("MONITOR_ACQUIRE",
                thread + " locks " + monitorName + " with no earlier release to acquire from.",
                thread + " входит в lock " + monitorName
                        + ", но более раннего release для acquire ещё нет.",
                List.of("thread:" + thread, "monitor:" + monitorName, "action:" + actionId),
                state());
    }

    public void unlock(String thread, String monitorName) {
        MonitorState monitor = monitors.computeIfAbsent(monitorName, MonitorState::new);
        if (!thread.equals(monitor.owner)) {
            throw new IllegalStateException(thread + " must own " + monitorName + " before unlock");
        }

        ThreadView view = thread(thread);
        view.status = "UNLOCKED";
        monitor.owner = null;

        String actionId = action(thread, "MONITOR_RELEASE", monitorName, null);
        monitor.lastReleaseAction = actionId;
        monitor.lastReleaseThread = thread;

        Trace.event("MONITOR_RELEASE",
                thread + " unlocks " + monitorName
                        + ". A later lock of the same monitor can acquire these writes.",
                thread + " выполняет unlock " + monitorName
                        + ". Более поздний lock того же monitor может получить эти записи.",
                List.of("thread:" + thread, "monitor:" + monitorName, "action:" + actionId),
                state());
    }

    public void startThread(String parentThread, String childThread) {
        ThreadView parent = thread(parentThread);
        parent.status = "STARTED_CHILD";
        String startAction = action(parentThread, "THREAD_START", childThread, null);

        ThreadView child = thread(childThread);
        child.status = "STARTED";
        String childAction = action(childThread, "THREAD_STARTED", parentThread, null);

        addEdge(startAction, childAction, "THREAD_START", childThread);
        refreshThread(child, "THREAD_START", parentThread);

        Trace.event("THREAD_START_EDGE",
                parentThread + " calls start() for " + childThread
                        + ". Actions before start() happen-before the new thread begins.",
                parentThread + " вызывает start() для " + childThread
                        + ". Действия до start() happen-before началу нового thread.",
                List.of("thread:" + parentThread, "thread:" + childThread,
                        "action:" + startAction, "action:" + childAction),
                state());
    }

    public void finishThread(String thread) {
        ThreadView view = thread(thread);
        view.status = "TERMINATED";
        String actionId = action(thread, "THREAD_TERMINATED", thread, null);
        terminatedActionByThread.put(thread, actionId);

        Trace.event("THREAD_TERMINATED",
                thread + " terminates. Its actions can be observed by a successful join().",
                thread + " завершился. Его действия может увидеть успешный join().",
                List.of("thread:" + thread, "action:" + actionId),
                state());
    }

    public void joinThread(String parentThread, String childThread) {
        String terminatedAction = terminatedActionByThread.get(childThread);
        if (terminatedAction == null) {
            throw new IllegalStateException(childThread + " must finish before joinThread");
        }

        ThreadView parent = thread(parentThread);
        parent.status = "JOINED";
        String joinAction = action(parentThread, "THREAD_JOIN", childThread, null);
        addEdge(terminatedAction, joinAction, "THREAD_JOIN", childThread);
        refreshThread(parent, "THREAD_JOIN", childThread);

        Trace.event("THREAD_JOIN_EDGE",
                parentThread + " joins " + childThread
                        + ". All actions in the terminated thread happen-before join() returns.",
                parentThread + " выполняет join для " + childThread
                        + ". Все действия завершённого thread happen-before возврату из join().",
                List.of("thread:" + parentThread, "thread:" + childThread,
                        "action:" + joinAction),
                state());
    }

    private void refreshThread(ThreadView view, String kind, String sourceThread) {
        for (Variable variable : variables.values()) {
            view.localValues.put(variable.name, variable.value);
        }
        view.lastRefreshKind = kind;
        view.lastRefreshSourceThread = sourceThread;
    }

    private String action(String threadName, String type, String target, Object value) {
        ThreadView view = thread(threadName);
        String id = "a" + nextActionId++;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("thread", threadName);
        item.put("type", type);
        item.put("target", target);
        item.put("value", value == null ? "" : value);
        actions.add(item);

        if (view.lastAction != null) {
            addEdge(view.lastAction, id, "PROGRAM_ORDER", threadName);
        }
        view.lastAction = id;
        return id;
    }

    private void addEdge(String from, String to, String kind, String detail) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("from", from);
        edge.put("to", to);
        edge.put("kind", kind);
        edge.put("detail", detail);
        edges.add(edge);
    }

    private Variable variable(String name, String kind) {
        return variables.computeIfAbsent(name, n -> new Variable(n, kind));
    }

    private ThreadView thread(String name) {
        Objects.requireNonNull(name, "name");
        return threads.computeIfAbsent(name, ThreadView::new);
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);

        List<Object> variableList = new ArrayList<>();
        for (Variable variable : variables.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", variable.name);
            item.put("kind", variable.kind);
            item.put("value", variable.value);
            item.put("lastWriter", variable.lastWriter == null ? "" : variable.lastWriter);
            variableList.add(item);
        }
        s.put("variables", variableList);

        List<Object> threadList = new ArrayList<>();
        for (ThreadView view : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", view.name);
            item.put("status", view.status);
            item.put("lastRefreshKind", view.lastRefreshKind == null ? "" : view.lastRefreshKind);
            item.put("lastRefreshSourceThread",
                    view.lastRefreshSourceThread == null ? "" : view.lastRefreshSourceThread);

            List<Object> localValues = new ArrayList<>();
            for (Variable variable : variables.values()) {
                Map<String, Object> local = new LinkedHashMap<>();
                local.put("name", variable.name);
                local.put("value", view.localValues.getOrDefault(variable.name, UNSET));
                localValues.add(local);
            }
            item.put("localValues", localValues);
            threadList.add(item);
        }
        s.put("threads", threadList);

        List<Object> monitorList = new ArrayList<>();
        for (MonitorState monitor : monitors.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", monitor.name);
            item.put("owner", monitor.owner == null ? "" : monitor.owner);
            item.put("lastReleaseThread",
                    monitor.lastReleaseThread == null ? "" : monitor.lastReleaseThread);
            monitorList.add(item);
        }
        s.put("monitors", monitorList);

        s.put("actions", new ArrayList<>(actions));
        s.put("edges", new ArrayList<>(edges));
        return s;
    }

    private static String show(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static final class Variable {
        final String name;
        String kind;
        Object value = UNSET;
        String lastWriter;
        String lastWriteAction;

        Variable(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    private static final class ThreadView {
        final String name;
        String status = "READY";
        String lastAction;
        String lastRefreshKind;
        String lastRefreshSourceThread;
        final Map<String, Object> localValues = new LinkedHashMap<>();

        ThreadView(String name) {
            this.name = name;
        }
    }

    private static final class MonitorState {
        final String name;
        String owner;
        String lastReleaseAction;
        String lastReleaseThread;

        MonitorState(String name) {
            this.name = name;
        }
    }
}
