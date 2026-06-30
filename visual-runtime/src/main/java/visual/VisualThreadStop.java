package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model for stopping an already-started Java thread.
 * It models cooperative cancellation and interruption without relying on real
 * scheduler timing, so topic examples produce stable trace events.
 */
public class VisualThreadStop {

    private static final int HISTORY_LIMIT = 12;

    private final String name;
    private final Map<String, WorkerInfo> workers = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    public VisualThreadStop() {
        this("thread-stop-demo");
    }

    public VisualThreadStop(String name) {
        this.name = Objects.requireNonNull(name, "name");
        addHistory("main", "CREATE_MODEL", name);
        Trace.event("THREAD_STOP_MODEL_CREATED",
                "Created thread-stopping scene '" + name + "'",
                "Создана сцена остановки thread '" + name + "'",
                List.of(),
                state());
    }

    public synchronized void createWorker(String workerName) {
        requireUnique(workerName);
        WorkerInfo worker = new WorkerInfo(workerName);
        workers.put(workerName, worker);
        addHistory("main", "CREATE_WORKER", workerName);
        Trace.event("THREAD_CREATED",
                "Created Thread object '" + workerName + "'. It is NEW until start() is called.",
                "Создан объект Thread '" + workerName + "'. Он находится в NEW, пока не вызван start().",
                List.of("worker:" + workerName),
                state());
    }

    public synchronized void start(String workerName) {
        WorkerInfo worker = worker(workerName);
        if (worker.started) {
            throw new IllegalStateException("'" + workerName + "' has already been started");
        }
        worker.started = true;
        worker.state = "RUNNING";
        addHistory("main", "START_WORKER", workerName);
        Trace.event("THREAD_STARTED",
                "start() launched '" + workerName + "'. From now on it must stop cooperatively.",
                "start() запустил '" + workerName + "'. Теперь его нужно останавливать кооперативно.",
                List.of("worker:" + workerName),
                state());
    }

    public synchronized void work(String workerName, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
        WorkerInfo worker = activeWorker(workerName);
        if ("WAITING".equals(worker.state) || "STOPPING".equals(worker.state)) {
            throw new IllegalStateException("'" + workerName + "' cannot do work while " + worker.state);
        }
        worker.workUnits += units;
        addHistory(workerName, "DO_WORK", units + " unit(s)");
        Trace.event("WORKER_STEP",
                "'" + workerName + "' did " + units
                        + " unit(s) of work. A stop request is not magic; code must check it.",
                "'" + workerName + "' выполнил " + units
                        + " единиц(ы) работы. Запрос остановки не магия; код должен его проверить.",
                List.of("worker:" + workerName),
                state());
    }

    public synchronized void requestStop(String workerName) {
        WorkerInfo worker = activeWorker(workerName);
        worker.stopRequested = true;
        if ("RUNNING".equals(worker.state)) {
            worker.state = "STOP_REQUESTED";
        }
        addHistory("main", "REQUEST_STOP", workerName);
        Trace.event("STOP_REQUESTED",
                "The owner set a cooperative stop flag for '" + workerName
                        + "'. The worker still has to observe it.",
                "Владелец установил кооперативный флаг остановки для '" + workerName
                        + "'. Worker ещё должен его заметить.",
                List.of("signal:" + workerName + ":stop", "worker:" + workerName),
                state());
    }

    public synchronized void observeStopRequest(String workerName) {
        WorkerInfo worker = activeWorker(workerName);
        if (!worker.stopRequested) {
            throw new IllegalStateException("'" + workerName + "' has no stop request to observe");
        }
        worker.state = "STOPPING";
        worker.lastObservation = "stop flag";
        addHistory(workerName, "OBSERVE_STOP", workerName);
        Trace.event("STOP_REQUEST_OBSERVED",
                "'" + workerName + "' checked the stop flag and chose a clean exit path.",
                "'" + workerName + "' проверил флаг остановки и выбрал аккуратный путь выхода.",
                List.of("signal:" + workerName + ":stop", "worker:" + workerName),
                state());
    }

    public synchronized void block(String workerName, String reason) {
        WorkerInfo worker = activeWorker(workerName);
        Objects.requireNonNull(reason, "reason");
        if (!"RUNNING".equals(worker.state) && !"STOP_REQUESTED".equals(worker.state)) {
            throw new IllegalStateException("'" + workerName + "' cannot block while " + worker.state);
        }
        worker.state = "WAITING";
        worker.waitReason = reason;
        addHistory(workerName, "BLOCK", reason);
        Trace.event("WORKER_BLOCKED",
                "'" + workerName + "' is blocked in " + reason
                        + "; a plain stop flag may not be noticed until the call returns.",
                "'" + workerName + "' заблокирован в " + reason
                        + "; обычный флаг остановки может быть не замечен, пока вызов не вернётся.",
                List.of("worker:" + workerName),
                state());
    }

    public synchronized void interrupt(String workerName) {
        WorkerInfo worker = activeWorker(workerName);
        worker.interruptSignalSent = true;
        if ("WAITING".equals(worker.state)) {
            worker.state = "STOPPING";
            worker.interruptedExceptionPending = true;
            worker.interruptStatus = false;
            worker.waitReason = null;
        } else {
            worker.interruptStatus = true;
        }
        addHistory("main", "INTERRUPT", workerName);
        Trace.event("THREAD_INTERRUPTED",
                "interrupt() sent a cancellation signal to '" + workerName
                        + "'. Blocking calls wake with InterruptedException; running loops must check the status.",
                "interrupt() отправил сигнал отмены в '" + workerName
                        + "'. Блокирующие вызовы просыпаются с InterruptedException; работающие циклы должны проверять статус.",
                List.of("signal:" + workerName + ":interrupt", "worker:" + workerName),
                state());
    }

    public synchronized void observeInterruptStatus(String workerName) {
        WorkerInfo worker = activeWorker(workerName);
        if (!worker.interruptStatus) {
            throw new IllegalStateException("'" + workerName + "' has no interrupt status to observe");
        }
        worker.state = "STOPPING";
        worker.lastObservation = "interrupt status";
        addHistory(workerName, "OBSERVE_INTERRUPT_STATUS", workerName);
        Trace.event("INTERRUPT_STATUS_OBSERVED",
                "'" + workerName + "' checked isInterrupted() and started a clean shutdown.",
                "'" + workerName + "' проверил isInterrupted() и начал аккуратное завершение.",
                List.of("signal:" + workerName + ":status", "worker:" + workerName),
                state());
    }

    public synchronized void handleInterruptedException(String workerName) {
        WorkerInfo worker = activeWorker(workerName);
        if (!worker.interruptedExceptionPending) {
            throw new IllegalStateException("'" + workerName + "' has no InterruptedException to handle");
        }
        worker.interruptedExceptionPending = false;
        worker.lastObservation = "InterruptedException";
        addHistory(workerName, "HANDLE_INTERRUPTED_EXCEPTION", workerName);
        Trace.event("INTERRUPT_OBSERVED",
                "'" + workerName + "' caught InterruptedException. The interrupt status was cleared by the exception.",
                "'" + workerName + "' поймал InterruptedException. Статус interrupt был очищен исключением.",
                List.of("signal:" + workerName + ":exception", "worker:" + workerName),
                state());
    }

    public synchronized void restoreInterruptStatus(String workerName) {
        WorkerInfo worker = activeWorker(workerName);
        worker.interruptStatus = true;
        worker.restoredInterrupt = true;
        addHistory(workerName, "RESTORE_INTERRUPT_STATUS", workerName);
        Trace.event("INTERRUPT_RESTORED",
                "'" + workerName + "' restored interrupt status so outer code can see the cancellation.",
                "'" + workerName + "' восстановил статус interrupt, чтобы внешний код увидел отмену.",
                List.of("signal:" + workerName + ":restored", "worker:" + workerName),
                state());
    }

    public synchronized void unsafeStopAttempt(String workerName) {
        WorkerInfo worker = activeWorker(workerName);
        worker.unsafeStopAttempts++;
        addHistory("main", "REJECT_THREAD_STOP", workerName);
        Trace.event("UNSAFE_STOP_REJECTED",
                "Thread.stop() would kill '" + workerName
                        + "' at an arbitrary point and may leave shared state broken, so reject it.",
                "Thread.stop() убил бы '" + workerName
                        + "' в произвольной точке и мог бы сломать общее состояние, поэтому отклоняем его.",
                List.of("worker:" + workerName),
                state());
    }

    public synchronized void exit(String workerName) {
        WorkerInfo worker = worker(workerName);
        if (!worker.started) {
            throw new IllegalStateException("'" + workerName + "' was never started");
        }
        if ("TERMINATED".equals(worker.state)) {
            throw new IllegalStateException("'" + workerName + "' is already terminated");
        }
        worker.state = "TERMINATED";
        worker.waitReason = null;
        addHistory(workerName, "EXIT", workerName);
        Trace.event("THREAD_EXITED",
                "'" + workerName + "' returned from run() and reached TERMINATED.",
                "'" + workerName + "' вернулся из run() и достиг TERMINATED.",
                List.of("worker:" + workerName),
                state());
    }

    public synchronized void join(String workerName) {
        WorkerInfo worker = worker(workerName);
        if (!"TERMINATED".equals(worker.state)) {
            throw new IllegalStateException("'" + workerName + "' must terminate before join() can complete");
        }
        worker.joined = true;
        addHistory("main", "JOIN", workerName);
        Trace.event("JOIN_COMPLETED",
                "main joined '" + workerName + "' and now knows the thread has fully stopped.",
                "main выполнил join для '" + workerName + "' и теперь знает, что thread полностью остановлен.",
                List.of("worker:" + workerName, "join:" + workerName),
                state());
    }

    private WorkerInfo activeWorker(String workerName) {
        WorkerInfo worker = worker(workerName);
        if (!worker.started) {
            throw new IllegalStateException("'" + workerName + "' has not been started");
        }
        if ("TERMINATED".equals(worker.state)) {
            throw new IllegalStateException("'" + workerName + "' is already terminated");
        }
        return worker;
    }

    private WorkerInfo worker(String workerName) {
        WorkerInfo worker = workers.get(workerName);
        if (worker == null) {
            throw new IllegalArgumentException("No worker named '" + workerName + "'");
        }
        return worker;
    }

    private void requireUnique(String workerName) {
        Objects.requireNonNull(workerName, "workerName");
        if (workers.containsKey(workerName)) {
            throw new IllegalArgumentException("Worker named '" + workerName + "' already exists");
        }
    }

    private void addHistory(String actor, String action, String target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
        item.put("action", action);
        item.put("target", target);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("controllerThread", "main");

        List<Object> workerList = new ArrayList<>();
        List<Object> signalList = new ArrayList<>();
        for (WorkerInfo worker : workers.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", worker.name);
            item.put("state", worker.state);
            item.put("started", worker.started);
            item.put("workUnits", worker.workUnits);
            item.put("joined", worker.joined);
            item.put("unsafeStopAttempts", worker.unsafeStopAttempts);
            if (worker.waitReason != null) {
                item.put("waitReason", worker.waitReason);
            }
            if (worker.lastObservation != null) {
                item.put("lastObservation", worker.lastObservation);
            }
            workerList.add(item);

            Map<String, Object> signals = new LinkedHashMap<>();
            signals.put("worker", worker.name);
            signals.put("stopRequested", worker.stopRequested);
            signals.put("interruptSignalSent", worker.interruptSignalSent);
            signals.put("interruptStatus", worker.interruptStatus);
            signals.put("interruptedExceptionPending", worker.interruptedExceptionPending);
            signals.put("restoredInterrupt", worker.restoredInterrupt);
            signalList.add(signals);
        }
        s.put("workers", workerList);
        s.put("signals", signalList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static final class WorkerInfo {
        final String name;
        String state = "NEW";
        boolean started;
        boolean stopRequested;
        boolean interruptSignalSent;
        boolean interruptStatus;
        boolean interruptedExceptionPending;
        boolean restoredInterrupt;
        boolean joined;
        int workUnits;
        int unsafeStopAttempts;
        String waitReason;
        String lastObservation;

        WorkerInfo(String name) {
            this.name = name;
        }
    }
}
