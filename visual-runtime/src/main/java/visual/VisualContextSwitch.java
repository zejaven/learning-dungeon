package visual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model of an operating-system context switch.
 * It does not use real scheduler timing; it models the pieces interviewers ask
 * about: ready queue, currently running thread, saved CPU context, blocking,
 * restoring and switch overhead.
 */
public class VisualContextSwitch {

    private static final int HISTORY_LIMIT = 12;

    private final String name;
    private final Map<String, ThreadInfo> threads = new LinkedHashMap<>();
    private final Deque<String> readyQueue = new ArrayDeque<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private String runningThread;
    private String cpuMode = "IDLE";
    private int sequence;
    private int contextSwitches;
    private int overheadTicks;
    private int usefulInstructions;

    public VisualContextSwitch() {
        this("context-switch-demo");
    }

    public VisualContextSwitch(String name) {
        this.name = Objects.requireNonNull(name, "name");
        addHistory("scheduler", "CREATE_MODEL", name);
        Trace.event("CONTEXT_SWITCH_MODEL_CREATED",
                "Created context-switch scene '" + name + "' with one CPU core",
                "Создана сцена context switch '" + name + "' с одним CPU core",
                List.of("cpu:cpu-0"),
                state());
    }

    public synchronized void addThread(String threadName) {
        addThread(threadName, 0);
    }

    public synchronized void addThread(String threadName, int initialPc) {
        Objects.requireNonNull(threadName, "threadName");
        if (threads.containsKey(threadName)) {
            throw new IllegalArgumentException("Thread named '" + threadName + "' already exists");
        }

        ThreadInfo thread = new ThreadInfo(threadName, initialPc, 1_000 + (++sequence * 100));
        threads.put(threadName, thread);
        readyQueue.addLast(threadName);
        addHistory("scheduler", "THREAD_READY", threadName);
        Trace.event("THREAD_READY",
                "Thread '" + threadName + "' is RUNNABLE and waits in the ready queue",
                "Thread '" + threadName + "' находится в RUNNABLE и ждет в ready queue",
                List.of("queue:ready", "thread:" + threadName),
                state());
    }

    public synchronized void dispatchNext() {
        if (runningThread != null) {
            throw new IllegalStateException("CPU already runs '" + runningThread + "'");
        }
        restoreNext("initial dispatch", null);
    }

    public synchronized void runInstructions(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }

        ThreadInfo thread = running();
        cpuMode = "USER";
        thread.pc += count;
        thread.registerA += count;
        thread.timeSliceUsed += count;
        usefulInstructions += count;
        addHistory(thread.name, "RUN_INSTRUCTIONS", count + " instructions");
        Trace.event("THREAD_EXECUTED",
                "Thread '" + thread.name + "' ran " + count
                        + " useful instruction(s); PC is now " + thread.pc,
                "Thread '" + thread.name + "' выполнил " + count
                        + " полезных инструкций; PC теперь " + thread.pc,
                List.of("cpu:cpu-0", "thread:" + thread.name),
                state());
    }

    public synchronized void expireTimeSlice() {
        ThreadInfo current = running();
        String previous = saveRunning("TIME_SLICE", "READY", true,
                "its time slice expired",
                "его квант времени истек");
        restoreNext("time slice expired for '" + current.name + "'", previous);
    }

    public synchronized void blockForIo(String resource) {
        Objects.requireNonNull(resource, "resource");
        ThreadInfo current = running();
        current.waitReason = resource;

        String previous = saveRunning("BLOCKED", "WAITING", false,
                "it blocked on " + resource,
                "он заблокировался на " + resource);
        addHistory("scheduler", "THREAD_BLOCKED", current.name + " -> " + resource);
        Trace.event("THREAD_BLOCKED",
                "Thread '" + current.name + "' is WAITING for " + resource
                        + "; the CPU can run another ready thread",
                "Thread '" + current.name + "' находится в WAITING из-за " + resource
                        + "; CPU может запустить другой ready thread",
                List.of("thread:" + current.name),
                state());
        restoreNext("blocked on " + resource, previous);
    }

    public synchronized void wake(String threadName) {
        ThreadInfo thread = thread(threadName);
        if (!"WAITING".equals(thread.state)) {
            throw new IllegalStateException("'" + threadName + "' is not WAITING");
        }
        String oldReason = thread.waitReason;
        thread.waitReason = null;
        thread.state = "READY";
        thread.saved = true;
        readyQueue.addLast(thread.name);
        addHistory("scheduler", "THREAD_WOKE", thread.name);
        Trace.event("THREAD_WOKE",
                "Thread '" + thread.name + "' finished waiting for " + oldReason
                        + " and returns to the ready queue",
                "Thread '" + thread.name + "' дождался " + oldReason
                        + " и возвращается в ready queue",
                List.of("queue:ready", "thread:" + thread.name),
                state());
    }

    public synchronized void finishRunning() {
        ThreadInfo current = running();
        String previous = current.name;
        cpuMode = "KERNEL";
        current.state = "TERMINATED";
        current.saved = false;
        current.lastSaveReason = "TERMINATED";
        runningThread = null;
        overheadTicks++;
        addHistory("kernel", "THREAD_FINISHED", current.name);
        Trace.event("THREAD_FINISHED",
                "Thread '" + current.name
                        + "' finished; the kernel discards its context instead of saving it for later",
                "Thread '" + current.name
                        + "' завершился; kernel удаляет его context вместо сохранения на потом",
                List.of("cpu:cpu-0", "thread:" + current.name),
                state());
        restoreNext("thread finished", previous);
    }

    private String saveRunning(String reason, String targetState, boolean requeue,
                               String reasonEn, String reasonRu) {
        ThreadInfo current = running();
        cpuMode = "KERNEL";
        runningThread = null;
        current.state = targetState;
        current.saved = true;
        current.lastSaveReason = reason;
        overheadTicks++;
        if (requeue) {
            readyQueue.addLast(current.name);
        }

        addHistory("kernel", "SAVE_CONTEXT", current.name + " (" + reason + ")");
        Trace.event("CONTEXT_SAVED",
                "Kernel saves PC=" + current.pc + ", SP=" + current.sp
                        + " and registers for '" + current.name + "' because " + reasonEn,
                "Kernel сохраняет PC=" + current.pc + ", SP=" + current.sp
                        + " и registers для '" + current.name + "', потому что " + reasonRu,
                List.of("cpu:cpu-0", "thread:" + current.name, "context:" + current.name),
                state());
        return current.name;
    }

    private void restoreNext(String reason, String previousThread) {
        if (readyQueue.isEmpty()) {
            cpuMode = "IDLE";
            runningThread = null;
            addHistory("scheduler", "CPU_IDLE", reason);
            Trace.event("CPU_IDLE",
                    "No READY thread exists, so the CPU core becomes idle",
                    "Нет READY thread, поэтому CPU core простаивает",
                    List.of("cpu:cpu-0"),
                    state());
            return;
        }

        ThreadInfo next = thread(readyQueue.removeFirst());
        cpuMode = "KERNEL";
        next.state = "RUNNING";
        next.saved = false;
        next.waitReason = null;
        next.timeSliceUsed = 0;
        next.runs++;
        runningThread = next.name;
        overheadTicks++;
        addHistory("kernel", "RESTORE_CONTEXT", next.name);
        Trace.event("CONTEXT_RESTORED",
                "Kernel restores saved PC=" + next.pc + ", SP=" + next.sp
                        + " and registers for '" + next.name + "'",
                "Kernel восстанавливает сохраненные PC=" + next.pc + ", SP=" + next.sp
                        + " и registers для '" + next.name + "'",
                List.of("cpu:cpu-0", "thread:" + next.name, "context:" + next.name),
                state());

        cpuMode = "USER";
        addHistory("cpu-0", "DISPATCH_THREAD", next.name);
        Trace.event("THREAD_DISPATCHED",
                "CPU core now runs thread '" + next.name + "' after " + reason,
                "CPU core теперь выполняет thread '" + next.name + "' после " + reason,
                List.of("cpu:cpu-0", "thread:" + next.name),
                state());

        if (previousThread != null) {
            if (!previousThread.equals(next.name)) {
                contextSwitches++;
            }
            addHistory("scheduler", "CONTEXT_SWITCH", previousThread + " -> " + next.name);
            String sameThread = previousThread.equals(next.name)
                    ? " The same thread was selected again because no other READY thread was ahead."
                    : "";
            String sameThreadRu = previousThread.equals(next.name)
                    ? " Тот же thread выбран снова, потому что впереди не было другого READY thread."
                    : "";
            Trace.event("CONTEXT_SWITCHED",
                    "Scheduler moved the CPU from '" + previousThread + "' to '" + next.name
                            + "'. Total switch overhead is " + overheadTicks + " tick(s)." + sameThread,
                    "Scheduler переключил CPU с '" + previousThread + "' на '" + next.name
                            + "'. Общие накладные расходы switch: " + overheadTicks + " tick(s)." + sameThreadRu,
                    List.of("cpu:cpu-0", "thread:" + previousThread, "thread:" + next.name),
                    state());
        }
    }

    private ThreadInfo running() {
        if (runningThread == null) {
            throw new IllegalStateException("No thread is currently running");
        }
        return thread(runningThread);
    }

    private ThreadInfo thread(String name) {
        ThreadInfo thread = threads.get(name);
        if (thread == null) {
            throw new IllegalArgumentException("No thread named '" + name + "'");
        }
        return thread;
    }

    private void addHistory(String actor, String action, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
        item.put("action", action);
        item.put("detail", detail);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);

        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("core", "cpu-0");
        cpu.put("mode", cpuMode);
        if (runningThread != null) {
            ThreadInfo running = thread(runningThread);
            cpu.put("runningThread", running.name);
            cpu.put("pc", running.pc);
            cpu.put("registerA", running.registerA);
            cpu.put("timeSliceUsed", running.timeSliceUsed);
        }
        s.put("cpu", cpu);

        List<Object> ready = new ArrayList<>();
        for (String threadName : readyQueue) {
            ready.add(threadState(thread(threadName)));
        }
        s.put("readyQueue", ready);

        List<Object> waiting = new ArrayList<>();
        List<Object> terminated = new ArrayList<>();
        List<Object> contexts = new ArrayList<>();
        for (ThreadInfo thread : threads.values()) {
            Map<String, Object> item = threadState(thread);
            contexts.add(item);
            if ("WAITING".equals(thread.state)) {
                waiting.add(item);
            } else if ("TERMINATED".equals(thread.state)) {
                terminated.add(item);
            }
        }
        s.put("waiting", waiting);
        s.put("terminated", terminated);
        s.put("contexts", contexts);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("contextSwitches", contextSwitches);
        metrics.put("overheadTicks", overheadTicks);
        metrics.put("usefulInstructions", usefulInstructions);
        s.put("metrics", metrics);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static Map<String, Object> threadState(ThreadInfo thread) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", thread.name);
        item.put("state", thread.state);
        item.put("pc", thread.pc);
        item.put("sp", thread.sp);
        item.put("registerA", thread.registerA);
        item.put("saved", thread.saved);
        item.put("runs", thread.runs);
        item.put("timeSliceUsed", thread.timeSliceUsed);
        if (thread.waitReason != null) {
            item.put("waitReason", thread.waitReason);
        }
        if (thread.lastSaveReason != null) {
            item.put("lastSaveReason", thread.lastSaveReason);
        }
        return item;
    }

    private static final class ThreadInfo {
        final String name;
        final int sp;
        String state = "READY";
        int pc;
        int registerA;
        int runs;
        int timeSliceUsed;
        boolean saved = true;
        String waitReason;
        String lastSaveReason = "CREATED";

        ThreadInfo(String name, int pc, int sp) {
            this.name = name;
            this.pc = pc;
            this.sp = sp;
        }
    }
}
