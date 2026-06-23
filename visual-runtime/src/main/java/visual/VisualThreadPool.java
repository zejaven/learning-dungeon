package visual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for the "Thread vs ThreadPool" interview topic.
 * It uses a real Java Thread for the per-task case, but simulates the pool so
 * queueing, reuse and rejection do not depend on scheduler timing.
 */
public class VisualThreadPool {

    private static final int HISTORY_LIMIT = 10;

    private final String name;
    private final Map<String, StandaloneThreadInfo> standaloneThreads = new LinkedHashMap<>();
    private final Map<String, PoolInfo> pools = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private int standaloneSequence;

    public VisualThreadPool() {
        this("thread-pool-demo");
    }

    public VisualThreadPool(String name) {
        this.name = Objects.requireNonNull(name, "name");
        Trace.event("THREAD_POOL_MODEL_CREATED",
                "Created Thread vs ThreadPool scene '" + name + "'",
                "Создана сцена Thread vs ThreadPool с именем '" + name + "'",
                List.of(),
                state());
    }

    public void runWithNewThread(String taskName, Runnable body) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(taskName, "taskName");

        String threadName;
        synchronized (this) {
            threadName = "thread-" + (++standaloneSequence);
            StandaloneThreadInfo info = new StandaloneThreadInfo(threadName, taskName);
            standaloneThreads.put(threadName, info);
            addHistory("main", "CREATE_NEW_THREAD", taskName);
            Trace.event("THREAD_CREATED_PER_TASK",
                    "Created brand-new Thread '" + threadName + "' for task '" + taskName + "'",
                    "Создан новый Thread '" + threadName + "' для задачи '" + taskName + "'",
                    List.of("thread:" + threadName, "task:" + taskName),
                    state());
        }

        RuntimeException[] failure = new RuntimeException[1];
        Thread thread = new Thread(() -> {
            synchronized (VisualThreadPool.this) {
                StandaloneThreadInfo info = standaloneThreads.get(threadName);
                info.state = "RUNNING";
                addHistory(threadName, "START_NEW_THREAD", taskName);
                Trace.event("THREAD_STARTED_PER_TASK",
                        "Started '" + threadName + "'. This task gets its own Java thread lifecycle.",
                        "Запущен '" + threadName + "'. Эта задача получает собственный жизненный цикл Java thread.",
                        List.of("thread:" + threadName, "task:" + taskName),
                        state());
            }
            try {
                body.run();
            } catch (RuntimeException e) {
                failure[0] = e;
            } finally {
                synchronized (VisualThreadPool.this) {
                    StandaloneThreadInfo info = standaloneThreads.get(threadName);
                    info.state = "TERMINATED";
                    addHistory(threadName, "TERMINATE_NEW_THREAD", taskName);
                    Trace.event("THREAD_TERMINATED_AFTER_TASK",
                            "'" + threadName + "' finished task '" + taskName + "' and cannot be reused",
                            "'" + threadName + "' завершил задачу '" + taskName + "' и не может быть переиспользован",
                            List.of("thread:" + threadName, "task:" + taskName),
                            state());
                }
            }
        }, threadName);

        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + threadName, e);
        }
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    public synchronized Pool fixedPool(String poolName, int workerCount, int queueCapacity) {
        Objects.requireNonNull(poolName, "poolName");
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity must not be negative");
        }
        if (pools.containsKey(poolName)) {
            throw new IllegalArgumentException("Pool named '" + poolName + "' already exists");
        }

        PoolInfo info = new PoolInfo(poolName, workerCount, queueCapacity);
        for (int i = 1; i <= workerCount; i++) {
            info.workers.add(new WorkerInfo("worker-" + i));
        }
        pools.put(poolName, info);
        addHistory(poolName, "CREATE_POOL", workerCount + " workers");
        Trace.event("THREAD_POOL_CREATED",
                "Created pool '" + poolName + "' with " + workerCount
                        + " reusable workers and queue capacity " + queueCapacity,
                "Создан pool '" + poolName + "' с " + workerCount
                        + " переиспользуемыми workers и емкостью очереди " + queueCapacity,
                List.of("pool:" + poolName),
                state());
        return new Pool(info);
    }

    public final class Pool {
        private final PoolInfo info;

        private Pool(PoolInfo info) {
            this.info = info;
        }

        public void submit(String taskName, Runnable body) {
            Objects.requireNonNull(taskName, "taskName");
            Objects.requireNonNull(body, "body");

            synchronized (VisualThreadPool.this) {
                if (info.tasks.containsKey(taskName)) {
                    throw new IllegalArgumentException("Task named '" + taskName + "' already exists in " + info.name);
                }

                TaskInfo task = new TaskInfo(taskName, body);
                info.tasks.put(taskName, task);
                addHistory(info.name, "SUBMIT_TASK", taskName);
                Trace.event("POOL_TASK_SUBMITTED",
                        "Submitted task '" + taskName + "' to pool '" + info.name + "'",
                        "Задача '" + taskName + "' отправлена в pool '" + info.name + "'",
                        List.of("pool:" + info.name, "task:" + taskName),
                        state());

                if (info.shutdown) {
                    reject(task, "SHUTDOWN");
                    return;
                }

                WorkerInfo idle = idleWorker(info);
                if (idle != null) {
                    assignTask(info, idle, task);
                    return;
                }

                if (info.queue.size() < info.queueCapacity) {
                    task.state = "QUEUED";
                    info.queue.addLast(task);
                    addHistory(info.name, "QUEUE_TASK", task.name);
                    Trace.event("POOL_TASK_QUEUED",
                            "All workers in '" + info.name + "' are busy, so task '" + task.name + "' waits in the queue",
                            "Все workers в '" + info.name + "' заняты, поэтому задача '" + task.name + "' ждет в очереди",
                            List.of("pool:" + info.name, "queue:" + info.name, "task:" + task.name),
                            state());
                    return;
                }

                reject(task, "QUEUE_FULL");
            }
        }

        public void completeOne() {
            WorkerInfo worker;
            synchronized (VisualThreadPool.this) {
                worker = busyWorker(info);
                if (worker == null) {
                    throw new IllegalStateException("Pool '" + info.name + "' has no running task to complete");
                }
            }
            complete(worker.name);
        }

        public void complete(String workerName) {
            TaskInfo task;
            synchronized (VisualThreadPool.this) {
                WorkerInfo worker = worker(info, workerName);
                if (worker.currentTask == null) {
                    throw new IllegalStateException(workerName + " has no running task");
                }
                task = info.tasks.get(worker.currentTask);
            }

            task.body.run();

            synchronized (VisualThreadPool.this) {
                WorkerInfo worker = worker(info, workerName);
                worker.state = "IDLE";
                worker.currentTask = null;
                worker.tasksCompleted++;
                task.state = "COMPLETED";
                addHistory(worker.name, "COMPLETE_TASK", task.name);
                Trace.event("POOL_TASK_COMPLETED",
                        "Worker '" + worker.name + "' completed task '" + task.name + "'",
                        "Worker '" + worker.name + "' завершил задачу '" + task.name + "'",
                        List.of("pool:" + info.name, "worker:" + info.name + "/" + worker.name, "task:" + task.name),
                        state());

                if (!info.queue.isEmpty()) {
                    TaskInfo next = info.queue.removeFirst();
                    assignTask(info, worker, next);
                }
            }
        }

        public void completeAll() {
            while (true) {
                WorkerInfo worker;
                synchronized (VisualThreadPool.this) {
                    worker = busyWorker(info);
                    if (worker == null) {
                        return;
                    }
                }
                complete(worker.name);
            }
        }

        public synchronized void shutdown() {
            synchronized (VisualThreadPool.this) {
                info.shutdown = true;
                addHistory(info.name, "SHUTDOWN_POOL", info.name);
                Trace.event("THREAD_POOL_SHUTDOWN",
                        "Pool '" + info.name + "' was shut down: it stops accepting new tasks but can finish existing work",
                        "Pool '" + info.name + "' остановлен: он больше не принимает новые задачи, но может завершить уже принятые",
                        List.of("pool:" + info.name),
                        state());
            }
        }

        private void reject(TaskInfo task, String reason) {
            task.state = "REJECTED";
            task.rejectionReason = reason;
            addHistory(info.name, "REJECT_TASK", task.name);
            String descEn = "Pool '" + info.name + "' rejected task '" + task.name + "' because "
                    + ("SHUTDOWN".equals(reason) ? "it is shut down" : "all workers and queue slots are full");
            String descRu = "Pool '" + info.name + "' отклонил задачу '" + task.name + "', потому что "
                    + ("SHUTDOWN".equals(reason) ? "он остановлен" : "все workers и места в очереди заняты");
            Trace.event("POOL_TASK_REJECTED",
                    descEn,
                    descRu,
                    List.of("pool:" + info.name, "task:" + task.name),
                    state());
        }
    }

    private void assignTask(PoolInfo pool, WorkerInfo worker, TaskInfo task) {
        task.state = "RUNNING";
        task.assignedWorker = worker.name;
        worker.state = "BUSY";
        worker.currentTask = task.name;
        addHistory(pool.name, "ASSIGN_TASK", task.name);
        Trace.event("POOL_TASK_ASSIGNED",
                "Task '" + task.name + "' is assigned to " + worker.name + " in pool '" + pool.name + "'",
                "Задача '" + task.name + "' назначена на " + worker.name + " в pool '" + pool.name + "'",
                List.of("pool:" + pool.name, "worker:" + pool.name + "/" + worker.name, "task:" + task.name),
                state());

        if (worker.tasksCompleted > 0) {
            Trace.event("POOL_WORKER_REUSED",
                    worker.name + " is reused for task '" + task.name + "' instead of creating a new Thread",
                    worker.name + " переиспользуется для задачи '" + task.name + "' вместо создания нового Thread",
                    List.of("pool:" + pool.name, "worker:" + pool.name + "/" + worker.name, "task:" + task.name),
                    state());
        }
    }

    private static WorkerInfo idleWorker(PoolInfo pool) {
        for (WorkerInfo worker : pool.workers) {
            if ("IDLE".equals(worker.state)) {
                return worker;
            }
        }
        return null;
    }

    private static WorkerInfo busyWorker(PoolInfo pool) {
        for (WorkerInfo worker : pool.workers) {
            if ("BUSY".equals(worker.state)) {
                return worker;
            }
        }
        return null;
    }

    private static WorkerInfo worker(PoolInfo pool, String name) {
        for (WorkerInfo worker : pool.workers) {
            if (worker.name.equals(name)) {
                return worker;
            }
        }
        throw new IllegalArgumentException("No worker named '" + name + "' in pool '" + pool.name + "'");
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

        List<Object> standalone = new ArrayList<>();
        for (StandaloneThreadInfo thread : standaloneThreads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", thread.name);
            item.put("task", thread.task);
            item.put("state", thread.state);
            standalone.add(item);
        }
        s.put("standaloneThreads", standalone);

        List<Object> poolList = new ArrayList<>();
        for (PoolInfo pool : pools.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", pool.name);
            item.put("workerCount", pool.workerCount);
            item.put("queueCapacity", pool.queueCapacity);
            item.put("shutdown", pool.shutdown);

            List<Object> workers = new ArrayList<>();
            for (WorkerInfo worker : pool.workers) {
                Map<String, Object> workerItem = new LinkedHashMap<>();
                workerItem.put("name", worker.name);
                workerItem.put("state", worker.state);
                workerItem.put("tasksCompleted", worker.tasksCompleted);
                if (worker.currentTask != null) {
                    workerItem.put("currentTask", worker.currentTask);
                }
                workers.add(workerItem);
            }
            item.put("workers", workers);

            List<Object> queue = new ArrayList<>();
            for (TaskInfo task : pool.queue) {
                queue.add(taskState(task));
            }
            item.put("queue", queue);

            List<Object> completed = new ArrayList<>();
            List<Object> rejected = new ArrayList<>();
            for (TaskInfo task : pool.tasks.values()) {
                if ("COMPLETED".equals(task.state)) {
                    completed.add(taskState(task));
                } else if ("REJECTED".equals(task.state)) {
                    rejected.add(taskState(task));
                }
            }
            item.put("completed", completed);
            item.put("rejected", rejected);
            poolList.add(item);
        }
        s.put("pools", poolList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static Map<String, Object> taskState(TaskInfo task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", task.name);
        item.put("state", task.state);
        if (task.assignedWorker != null) {
            item.put("assignedWorker", task.assignedWorker);
        }
        if (task.rejectionReason != null) {
            item.put("rejectionReason", task.rejectionReason);
        }
        return item;
    }

    private static final class StandaloneThreadInfo {
        final String name;
        final String task;
        String state = "NEW";

        StandaloneThreadInfo(String name, String task) {
            this.name = name;
            this.task = task;
        }
    }

    private static final class PoolInfo {
        final String name;
        final int workerCount;
        final int queueCapacity;
        final List<WorkerInfo> workers = new ArrayList<>();
        final Deque<TaskInfo> queue = new ArrayDeque<>();
        final Map<String, TaskInfo> tasks = new LinkedHashMap<>();
        boolean shutdown;

        PoolInfo(String name, int workerCount, int queueCapacity) {
            this.name = name;
            this.workerCount = workerCount;
            this.queueCapacity = queueCapacity;
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

    private static final class TaskInfo {
        final String name;
        final Runnable body;
        String state = "SUBMITTED";
        String assignedWorker;
        String rejectionReason;

        TaskInfo(String name, Runnable body) {
            this.name = name;
            this.body = body;
        }
    }
}
