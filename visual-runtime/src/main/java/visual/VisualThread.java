package visual;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for the "Thread vs Runnable" interview topic.
 * It uses real {@link Thread} and {@link Runnable} types, but the helper
 * {@link #start(Thread)} waits for completion so examples do not depend on
 * scheduler timing.
 */
public class VisualThread {

    private static final int HISTORY_LIMIT = 8;

    private final String name;
    private final Map<String, ThreadInfo> threads = new LinkedHashMap<>();
    private final Map<String, RunnableInfo> runnables = new LinkedHashMap<>();
    private final IdentityHashMap<Thread, ThreadInfo> threadInfos = new IdentityHashMap<>();
    private final IdentityHashMap<Runnable, RunnableInfo> runnableInfos = new IdentityHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private String currentThread = "main";

    public VisualThread() {
        this("thread-vs-runnable");
    }

    public VisualThread(String name) {
        this.name = Objects.requireNonNull(name, "name");
        Trace.event("THREAD_MODEL_CREATED",
                "Created a Thread vs Runnable scene named '" + name + "'",
                "Создана сцена Thread vs Runnable с именем '" + name + "'",
                List.of(),
                state());
    }

    public Runnable runnable(String taskName, Runnable body) {
        Objects.requireNonNull(body, "body");
        requireUnique(runnables, taskName, "Runnable");

        RunnableInfo info = new RunnableInfo(taskName);
        runnables.put(taskName, info);
        VisualRunnable visualRunnable = new VisualRunnable(info, body);
        runnableInfos.put(visualRunnable, info);

        addHistory("main", "CREATE_RUNNABLE", taskName);
        Trace.event("RUNNABLE_CREATED",
                "Created Runnable task '" + taskName + "'. It describes work but does not start a thread.",
                "Создана задача Runnable '" + taskName + "'. Она описывает работу, но не запускает thread.",
                List.of("task:" + taskName),
                state());
        return visualRunnable;
    }

    public Thread thread(String threadName, Runnable task) {
        Objects.requireNonNull(task, "task");
        requireUnique(threads, threadName, "Thread");

        RunnableInfo runnableInfo = runnableInfoFor(task);
        runnableInfo.attachedTo.add(threadName);
        if ("READY".equals(runnableInfo.state)) {
            runnableInfo.state = "ATTACHED";
        }

        ThreadInfo info = new ThreadInfo(threadName, "THREAD_WITH_RUNNABLE", runnableInfo.name);
        threads.put(threadName, info);
        VisualJavaThread thread = new VisualJavaThread(info, task, null);
        threadInfos.put(thread, info);

        addHistory("main", "CREATE_THREAD", threadName);
        Trace.event("THREAD_CREATED_WITH_RUNNABLE",
                "Created Thread '" + threadName + "' and gave it Runnable '" + runnableInfo.name + "'",
                "Создан Thread '" + threadName + "', которому передан Runnable '" + runnableInfo.name + "'",
                List.of("thread:" + threadName, "task:" + runnableInfo.name),
                state());

        if (runnableInfo.attachedTo.size() > 1) {
            addHistory("main", "REUSE_RUNNABLE", runnableInfo.name);
            Trace.event("RUNNABLE_REUSED",
                    "The same Runnable '" + runnableInfo.name + "' is attached to multiple Thread objects",
                    "Один и тот же Runnable '" + runnableInfo.name + "' привязан к нескольким объектам Thread",
                    List.of("task:" + runnableInfo.name, "thread:" + threadName),
                    state());
        }
        return thread;
    }

    public Thread threadSubclass(String threadName, Runnable overriddenRunBody) {
        Objects.requireNonNull(overriddenRunBody, "overriddenRunBody");
        requireUnique(threads, threadName, "Thread");

        ThreadInfo info = new ThreadInfo(threadName, "THREAD_SUBCLASS", "overridden run()");
        threads.put(threadName, info);
        VisualJavaThread thread = new VisualJavaThread(info, null, overriddenRunBody);
        threadInfos.put(thread, info);

        addHistory("main", "CREATE_SUBCLASS", threadName);
        Trace.event("THREAD_SUBCLASS_CREATED",
                "Created Thread subclass '" + threadName + "'. The worker object owns its run() code.",
                "Создан подкласс Thread '" + threadName + "'. Объект worker сам содержит свой код run().",
                List.of("thread:" + threadName),
                state());
        return thread;
    }

    public void start(Thread thread) {
        ThreadInfo info = requireThread(thread);
        if (info.started) {
            throw new IllegalStateException(info.name + " has already been started");
        }

        synchronized (this) {
            info.started = true;
            info.state = "RUNNABLE";
            currentThread = "main";
            addHistory("main", "START_THREAD", info.name);
            Trace.event("THREAD_STARTED",
                    "start() asks the JVM to run '" + info.name + "' on a separate Java thread",
                    "start() просит JVM выполнить '" + info.name + "' в отдельном Java thread",
                    List.of("thread:" + info.name, "current"),
                    state());
        }

        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + info.name, e);
        }

        synchronized (this) {
            currentThread = "main";
        }
    }

    public void callRunDirectly(Thread thread) {
        ThreadInfo info = requireThread(thread);
        synchronized (this) {
            currentThread = Thread.currentThread().getName();
            info.state = "DIRECT_RUN";
            addHistory(currentThread, "CALL_RUN_DIRECTLY", info.name);
            Trace.event("THREAD_RUN_CALLED_DIRECTLY",
                    "Calling run() directly executes '" + info.name + "' like a normal method on the current thread",
                    "Прямой вызов run() выполняет '" + info.name + "' как обычный метод в текущем thread",
                    List.of("thread:" + info.name, "current"),
                    state());
        }
        thread.run();
        synchronized (this) {
            if (!info.started) {
                info.state = "NEW";
            }
            currentThread = "main";
        }
    }

    private synchronized void beginThreadRun(ThreadInfo info, boolean directCall) {
        currentThread = Thread.currentThread().getName();
        info.executionThread = currentThread;
        info.state = directCall ? "DIRECT_RUN" : "RUNNING";
        addHistory(currentThread, "ENTER_RUN", info.name);

        if ("THREAD_SUBCLASS".equals(info.kind)) {
            Trace.event("THREAD_RUN_EXECUTED",
                    "Thread subclass '" + info.name + "' is executing its own run() method",
                    "Подкласс Thread '" + info.name + "' выполняет свой собственный метод run()",
                    List.of("thread:" + info.name, "current"),
                    state());
        }
    }

    private synchronized void finishThreadRun(ThreadInfo info, boolean directCall) {
        if (directCall && !info.started) {
            info.state = "NEW";
            info.executionThread = currentThread;
        } else {
            info.state = "TERMINATED";
            info.executionThread = null;
        }
        addHistory(Thread.currentThread().getName(), directCall ? "RETURN_RUN" : "TERMINATE_THREAD", info.name);
        Trace.event("THREAD_TERMINATED",
                directCall
                        ? "run() returned, but '" + info.name + "' was never started as a separate thread"
                        : "'" + info.name + "' finished and reached TERMINATED state",
                directCall
                        ? "run() вернулся, но '" + info.name + "' так и не был запущен как отдельный thread"
                        : "'" + info.name + "' завершился и перешел в состояние TERMINATED",
                List.of("thread:" + info.name),
                state());
    }

    private synchronized void beginRunnable(RunnableInfo info) {
        currentThread = Thread.currentThread().getName();
        info.state = "RUNNING";
        info.runs++;
        addHistory(currentThread, "RUN_TASK", info.name);
        Trace.event("RUNNABLE_EXECUTED",
                "Runnable '" + info.name + "' is now executing on thread '" + currentThread + "'",
                "Runnable '" + info.name + "' сейчас выполняется в thread '" + currentThread + "'",
                List.of("task:" + info.name, "current"),
                state());
    }

    private synchronized void finishRunnable(RunnableInfo info) {
        info.state = "DONE";
    }

    private RunnableInfo runnableInfoFor(Runnable task) {
        RunnableInfo info = runnableInfos.get(task);
        if (info != null) {
            return info;
        }

        String taskName = "externalRunnable" + (runnables.size() + 1);
        info = new RunnableInfo(taskName);
        runnables.put(taskName, info);
        runnableInfos.put(task, info);
        return info;
    }

    private ThreadInfo requireThread(Thread thread) {
        ThreadInfo info = threadInfos.get(thread);
        if (info == null) {
            throw new IllegalArgumentException("Thread was not created by this VisualThread model");
        }
        return info;
    }

    private static void requireUnique(Map<String, ?> map, String name, String kind) {
        Objects.requireNonNull(name, "name");
        if (map.containsKey(name)) {
            throw new IllegalArgumentException(kind + " named '" + name + "' already exists");
        }
    }

    private synchronized void addHistory(String actor, String action, String target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
        item.put("action", action);
        item.put("target", target);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private synchronized Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("currentThread", currentThread);

        List<Object> runnableList = new ArrayList<>();
        for (RunnableInfo info : runnables.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", info.name);
            item.put("state", info.state);
            item.put("attachedTo", new ArrayList<>(info.attachedTo));
            item.put("runs", info.runs);
            runnableList.add(item);
        }
        s.put("runnables", runnableList);

        List<Object> threadList = new ArrayList<>();
        for (ThreadInfo info : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", info.name);
            item.put("state", info.state);
            item.put("kind", info.kind);
            item.put("task", info.task);
            item.put("started", info.started);
            if (info.executionThread != null) {
                item.put("executionThread", info.executionThread);
            }
            threadList.add(item);
        }
        s.put("threads", threadList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private final class VisualRunnable implements Runnable {
        private final RunnableInfo info;
        private final Runnable body;

        private VisualRunnable(RunnableInfo info, Runnable body) {
            this.info = info;
            this.body = body;
        }

        @Override
        public void run() {
            beginRunnable(info);
            try {
                body.run();
            } finally {
                finishRunnable(info);
            }
        }
    }

    private final class VisualJavaThread extends Thread {
        private final ThreadInfo info;
        private final Runnable target;
        private final Runnable subclassBody;

        private VisualJavaThread(ThreadInfo info, Runnable target, Runnable subclassBody) {
            super(info.name);
            this.info = info;
            this.target = target;
            this.subclassBody = subclassBody;
        }

        @Override
        public void run() {
            boolean directCall = Thread.currentThread() != this;
            beginThreadRun(info, directCall);
            try {
                if (subclassBody != null) {
                    subclassBody.run();
                } else {
                    target.run();
                }
            } finally {
                finishThreadRun(info, directCall);
            }
        }
    }

    private static final class RunnableInfo {
        final String name;
        final List<String> attachedTo = new ArrayList<>();
        String state = "READY";
        int runs;

        RunnableInfo(String name) {
            this.name = name;
        }
    }

    private static final class ThreadInfo {
        final String name;
        final String kind;
        final String task;
        String state = "NEW";
        boolean started;
        String executionThread;

        ThreadInfo(String name, String kind, String task) {
            this.name = name;
            this.kind = kind;
            this.task = task;
        }
    }
}
