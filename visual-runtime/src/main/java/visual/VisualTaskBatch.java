package visual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for running many tasks on a limited executor
 * and waiting until all accepted work is done. It names simulated workers and a
 * simulated main thread, so examples can demonstrate CountDownLatch, invokeAll
 * and Future.get without depending on scheduler timing.
 */
public class VisualTaskBatch {

    private static final int HISTORY_LIMIT = 10;

    private final String name;
    private final Map<String, TaskInfo> tasks = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private ExecutorInfo executor;
    private LatchInfo latch;
    private String mainState = "RUNNING";
    private String mainWaitingFor;

    public VisualTaskBatch() {
        this("task-batch");
    }

    public VisualTaskBatch(String name) {
        this.name = Objects.requireNonNull(name, "name");
        addHistory("main", "CREATE_SCENE", name);
        Trace.event("TASK_BATCH_CREATED",
                "Created task batch scene '" + name + "'",
                "Создана сцена task batch '" + name + "'",
                List.of("main"),
                state());
    }

    public synchronized Executor fixedExecutor(String executorName, int workerCount) {
        Objects.requireNonNull(executorName, "executorName");
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (executor != null) {
            throw new IllegalStateException("This scene already has an executor");
        }

        ExecutorInfo info = new ExecutorInfo(executorName, workerCount);
        for (int i = 1; i <= workerCount; i++) {
            info.workers.add(new WorkerInfo("worker-" + i));
        }
        executor = info;
        addHistory("main", "CREATE_EXECUTOR", executorName);
        Trace.event("LIMITED_EXECUTOR_CREATED",
                "Created fixed executor '" + executorName + "' with " + workerCount
                        + " worker(s)",
                "Создан fixed executor '" + executorName + "' с worker(s): " + workerCount,
                List.of("executor"),
                state());
        return new Executor(info);
    }

    public synchronized Latch countDownLatch(String latchName, int count) {
        Objects.requireNonNull(latchName, "latchName");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        latch = new LatchInfo(latchName, count);
        addHistory("main", "CREATE_LATCH", latchName);
        Trace.event("LATCH_CREATED",
                "Created CountDownLatch '" + latchName + "' with count " + count,
                "Создан CountDownLatch '" + latchName + "' со счетчиком " + count,
                List.of("latch"),
                state());
        return new Latch(latch);
    }

    public final class Executor {
        private final ExecutorInfo info;

        private Executor(ExecutorInfo info) {
            this.info = info;
        }

        public void submit(String taskName) {
            submit(taskName, () -> {
            });
        }

        public void submit(String taskName, Runnable onFinish) {
            submitInternal(info, taskName, onFinish, false, null);
        }

        public <T> FutureRef<T> submitFuture(String taskName, T result) {
            TaskInfo task = submitInternal(info, taskName, () -> {
            }, true, result);
            synchronized (VisualTaskBatch.this) {
                addHistory("main", "CREATE_FUTURE", taskName);
                Trace.event("FUTURE_CREATED",
                        "submit(...) returned a Future for task '" + taskName + "'",
                        "submit(...) вернул Future для задачи '" + taskName + "'",
                        List.of("future:" + taskName, "task:" + taskName),
                        state());
            }
            return new FutureRef<>(task);
        }

        public List<FutureRef<String>> invokeAll(List<String> taskNames) {
            return invokeAll("main", taskNames);
        }

        public List<FutureRef<String>> invokeAll(String actor, List<String> taskNames) {
            Objects.requireNonNull(taskNames, "taskNames");
            synchronized (VisualTaskBatch.this) {
                mainState = "WAITING_INVOKE_ALL";
                mainWaitingFor = "invokeAll";
                addHistory(actor, "INVOKE_ALL", taskNames.size() + " tasks");
                Trace.event("INVOKE_ALL_CALLED",
                        actor + " called invokeAll(...): all tasks are submitted and main waits for every result",
                        actor + " вызвал invokeAll(...): все задачи отправляются, а main ждет все результаты",
                        List.of("main", "executor"),
                        state());
            }

            List<FutureRef<String>> futures = new ArrayList<>();
            for (String taskName : taskNames) {
                futures.add(submitFuture(taskName, taskName + "-result"));
            }
            completeAll();

            synchronized (VisualTaskBatch.this) {
                mainState = "RUNNING";
                mainWaitingFor = null;
                addHistory(actor, "INVOKE_ALL_DONE", taskNames.size() + " tasks");
                Trace.event("INVOKE_ALL_COMPLETED",
                        "invokeAll(...) returned only after every submitted task completed",
                        "invokeAll(...) вернулся только после завершения всех отправленных задач",
                        List.of("main", "executor"),
                        state());
            }
            return futures;
        }

        public void completeOne() {
            WorkerInfo worker;
            synchronized (VisualTaskBatch.this) {
                worker = firstBusyWorker(info);
                if (worker == null) {
                    throw new IllegalStateException("No running task to complete");
                }
            }
            complete(worker.name);
        }

        public void complete(String workerName) {
            TaskInfo completed;
            Runnable onFinish;
            synchronized (VisualTaskBatch.this) {
                WorkerInfo worker = worker(info, workerName);
                if (worker.currentTask == null) {
                    throw new IllegalStateException(workerName + " has no running task");
                }

                completed = tasks.get(worker.currentTask);
                worker.currentTask = null;
                worker.tasksCompleted++;
                worker.state = "IDLE";
                completed.state = "COMPLETED";
                onFinish = completed.onFinish;

                addHistory(worker.name, "FINISH_TASK", completed.name);
                Trace.event("TASK_FINISHED",
                        worker.name + " finished task '" + completed.name + "'",
                        worker.name + " завершил задачу '" + completed.name + "'",
                        List.of("worker:" + worker.name, "task:" + completed.name),
                        state());

                if (completed.future) {
                    Trace.event("FUTURE_COMPLETED",
                            "Future for task '" + completed.name + "' is now done",
                            "Future для задачи '" + completed.name + "' теперь done",
                            List.of("future:" + completed.name, "task:" + completed.name),
                            state());
                }
            }

            try {
                onFinish.run();
            } finally {
                synchronized (VisualTaskBatch.this) {
                    WorkerInfo idle = firstIdleWorker(info);
                    if (idle != null && !info.queue.isEmpty()) {
                        assign(info, idle, info.queue.removeFirst());
                    }
                }
            }
        }

        public void completeAll() {
            while (true) {
                WorkerInfo worker;
                synchronized (VisualTaskBatch.this) {
                    worker = firstBusyWorker(info);
                    if (worker == null) {
                        return;
                    }
                }
                complete(worker.name);
            }
        }

        public synchronized void shutdown() {
            synchronized (VisualTaskBatch.this) {
                info.shutdown = true;
                addHistory("main", "SHUTDOWN", info.name);
                Trace.event("EXECUTOR_SHUTDOWN",
                        "Executor '" + info.name + "' was shut down after accepted work was handled",
                        "Executor '" + info.name + "' остановлен после обработки принятой работы",
                        List.of("executor"),
                        state());
            }
        }
    }

    public final class Latch {
        private final LatchInfo info;

        private Latch(LatchInfo info) {
            this.info = info;
        }

        public boolean await() {
            return await("main");
        }

        public synchronized boolean await(String actor) {
            synchronized (VisualTaskBatch.this) {
                if (info.count == 0) {
                    mainState = "RUNNING";
                    mainWaitingFor = null;
                    addHistory(actor, "AWAIT_PASSED", info.name);
                    Trace.event("MAIN_RELEASED_BY_LATCH",
                            actor + " passed CountDownLatch.await() because the count is already zero",
                            actor + " прошел CountDownLatch.await(), потому что счетчик уже равен нулю",
                            List.of("main", "latch"),
                            state());
                    return true;
                }

                mainState = "WAITING_ON_LATCH";
                mainWaitingFor = info.name;
                addHistory(actor, "AWAIT_LATCH", info.name);
                Trace.event("MAIN_AWAITING_LATCH",
                        actor + " is waiting in CountDownLatch.await() until count reaches zero",
                        actor + " ждет в CountDownLatch.await(), пока счетчик не станет нулем",
                        List.of("main", "latch"),
                        state());
                return false;
            }
        }

        public synchronized void countDown(String actor) {
            synchronized (VisualTaskBatch.this) {
                if (info.count > 0) {
                    info.count--;
                }
                addHistory(actor, "COUNT_DOWN", info.name + "=" + info.count);
                Trace.event("LATCH_COUNTED_DOWN",
                        actor + " called countDown(); remaining count is " + info.count,
                        actor + " вызвал countDown(); осталось " + info.count,
                        List.of("latch"),
                        state());

                if (info.count == 0 && "WAITING_ON_LATCH".equals(mainState)
                        && info.name.equals(mainWaitingFor)) {
                    mainState = "RUNNING";
                    mainWaitingFor = null;
                    addHistory("main", "LATCH_RELEASED", info.name);
                    Trace.event("MAIN_RELEASED_BY_LATCH",
                            "The latch reached zero, so main continues after await()",
                            "Latch достиг нуля, поэтому main продолжает после await()",
                            List.of("main", "latch"),
                            state());
                }
            }
        }

        public int getCount() {
            synchronized (VisualTaskBatch.this) {
                return info.count;
            }
        }
    }

    public final class FutureRef<T> {
        private final TaskInfo task;

        private FutureRef(TaskInfo task) {
            this.task = task;
        }

        public boolean isDone() {
            synchronized (VisualTaskBatch.this) {
                return "COMPLETED".equals(task.state);
            }
        }

        public T get() {
            return get("main");
        }

        @SuppressWarnings("unchecked")
        public T get(String actor) {
            synchronized (VisualTaskBatch.this) {
                if (!"COMPLETED".equals(task.state)) {
                    mainState = "WAITING_ON_FUTURE";
                    mainWaitingFor = task.name;
                    addHistory(actor, "GET_FUTURE_WAIT", task.name);
                    Trace.event("FUTURE_GET_WAITING",
                            actor + " called Future.get() before task '" + task.name
                                    + "' completed, so it waits",
                            actor + " вызвал Future.get() до завершения задачи '" + task.name
                                    + "', поэтому он ждет",
                            List.of("main", "future:" + task.name, "task:" + task.name),
                            state());
                    return null;
                }

                mainState = "RUNNING";
                mainWaitingFor = null;
                addHistory(actor, "GET_FUTURE_OK", task.name);
                Trace.event("FUTURE_GET_RETURNED",
                        "Future.get() returned the result of task '" + task.name + "'",
                        "Future.get() вернул результат задачи '" + task.name + "'",
                        List.of("main", "future:" + task.name, "task:" + task.name),
                        state());
                return (T) task.result;
            }
        }
    }

    private TaskInfo submitInternal(ExecutorInfo info, String taskName, Runnable onFinish,
                                    boolean future, Object result) {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(onFinish, "onFinish");
        synchronized (this) {
            if (info.shutdown) {
                throw new IllegalStateException("Executor '" + info.name + "' is shut down");
            }
            if (tasks.containsKey(taskName)) {
                throw new IllegalArgumentException("Task named '" + taskName + "' already exists");
            }

            TaskInfo task = new TaskInfo(taskName, onFinish, future, result);
            tasks.put(taskName, task);
            info.tasks.put(taskName, task);
            addHistory("main", "SUBMIT_TASK", taskName);
            Trace.event("TASK_SUBMITTED",
                    "Submitted task '" + taskName + "' to the limited executor",
                    "Задача '" + taskName + "' отправлена в limited executor",
                    List.of("executor", "task:" + taskName),
                    state());

            WorkerInfo idle = firstIdleWorker(info);
            if (idle == null) {
                task.state = "QUEUED";
                info.queue.addLast(task);
                addHistory(info.name, "QUEUE_TASK", taskName);
                Trace.event("TASK_QUEUED",
                        "All workers are busy, so task '" + taskName + "' waits in the queue",
                        "Все workers заняты, поэтому задача '" + taskName + "' ждет в очереди",
                        List.of("executor", "queue", "task:" + taskName),
                        state());
            } else {
                assign(info, idle, task);
            }
            return task;
        }
    }

    private void assign(ExecutorInfo info, WorkerInfo worker, TaskInfo task) {
        task.state = "RUNNING";
        task.assignedWorker = worker.name;
        worker.state = "BUSY";
        worker.currentTask = task.name;
        addHistory(info.name, "START_TASK", task.name);
        Trace.event("TASK_STARTED",
                "Task '" + task.name + "' started on " + worker.name,
                "Задача '" + task.name + "' стартовала на " + worker.name,
                List.of("worker:" + worker.name, "task:" + task.name),
                state());
    }

    private static WorkerInfo firstIdleWorker(ExecutorInfo info) {
        for (WorkerInfo worker : info.workers) {
            if ("IDLE".equals(worker.state)) {
                return worker;
            }
        }
        return null;
    }

    private static WorkerInfo firstBusyWorker(ExecutorInfo info) {
        for (WorkerInfo worker : info.workers) {
            if ("BUSY".equals(worker.state)) {
                return worker;
            }
        }
        return null;
    }

    private static WorkerInfo worker(ExecutorInfo info, String name) {
        for (WorkerInfo worker : info.workers) {
            if (worker.name.equals(name)) {
                return worker;
            }
        }
        throw new IllegalArgumentException("No worker named '" + name + "'");
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

        Map<String, Object> main = new LinkedHashMap<>();
        main.put("state", mainState);
        if (mainWaitingFor != null) {
            main.put("waitingFor", mainWaitingFor);
        }
        s.put("main", main);
        s.put("executor", executor == null ? null : executorState(executor));
        s.put("latch", latch == null ? null : latchState(latch));

        List<Object> futures = new ArrayList<>();
        for (TaskInfo task : tasks.values()) {
            if (task.future) {
                Map<String, Object> item = taskState(task);
                item.put("done", "COMPLETED".equals(task.state));
                if ("COMPLETED".equals(task.state)) {
                    item.put("result", task.result);
                }
                futures.add(item);
            }
        }
        s.put("futures", futures);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static Map<String, Object> executorState(ExecutorInfo info) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", info.name);
        item.put("workerCount", info.workerCount);
        item.put("shutdown", info.shutdown);

        List<Object> workers = new ArrayList<>();
        for (WorkerInfo worker : info.workers) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("name", worker.name);
            w.put("state", worker.state);
            w.put("tasksCompleted", worker.tasksCompleted);
            if (worker.currentTask != null) {
                w.put("currentTask", worker.currentTask);
            }
            workers.add(w);
        }
        item.put("workers", workers);

        List<Object> queue = new ArrayList<>();
        for (TaskInfo task : info.queue) {
            queue.add(taskState(task));
        }
        item.put("queue", queue);

        List<Object> completed = new ArrayList<>();
        for (TaskInfo task : info.tasks.values()) {
            if ("COMPLETED".equals(task.state)) {
                completed.add(taskState(task));
            }
        }
        item.put("completed", completed);
        return item;
    }

    private static Map<String, Object> latchState(LatchInfo info) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", info.name);
        item.put("initialCount", info.initialCount);
        item.put("count", info.count);
        item.put("released", info.count == 0);
        return item;
    }

    private static Map<String, Object> taskState(TaskInfo task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", task.name);
        item.put("state", task.state);
        if (task.assignedWorker != null) {
            item.put("assignedWorker", task.assignedWorker);
        }
        return item;
    }

    private static final class ExecutorInfo {
        final String name;
        final int workerCount;
        final List<WorkerInfo> workers = new ArrayList<>();
        final Deque<TaskInfo> queue = new ArrayDeque<>();
        final Map<String, TaskInfo> tasks = new LinkedHashMap<>();
        boolean shutdown;

        ExecutorInfo(String name, int workerCount) {
            this.name = name;
            this.workerCount = workerCount;
        }
    }

    private static final class WorkerInfo {
        final String name;
        String state = "IDLE";
        String currentTask;
        int tasksCompleted;

        WorkerInfo(String name) {
            this.name = name;
        }
    }

    private static final class LatchInfo {
        final String name;
        final int initialCount;
        int count;

        LatchInfo(String name, int count) {
            this.name = name;
            this.initialCount = count;
            this.count = count;
        }
    }

    private static final class TaskInfo {
        final String name;
        final Runnable onFinish;
        final boolean future;
        final Object result;
        String state = "SUBMITTED";
        String assignedWorker;

        TaskInfo(String name, Runnable onFinish, boolean future, Object result) {
            this.name = name;
            this.onFinish = onFinish;
            this.future = future;
            this.result = result;
        }
    }
}
