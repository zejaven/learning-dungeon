package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deterministic teaching model of {@link java.util.concurrent.Semaphore}.
 * It does not block real Java threads; instead, examples name simulated
 * threads so the visualization can show permits, waiting, handoff, tryAcquire
 * and the ownership trap without relying on scheduler timing.
 */
public class VisualSemaphore {

    private static final int HISTORY_LIMIT = 6;

    private final String name;
    private final int initialPermits;
    private final boolean fair;
    private final Map<String, Integer> holders = new LinkedHashMap<>();
    private final List<Waiter> waitingQueue = new ArrayList<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private int availablePermits;

    public VisualSemaphore(int permits) {
        this("semaphore", permits, true);
    }

    public VisualSemaphore(String name, int permits) {
        this(name, permits, true);
    }

    public VisualSemaphore(String name, int permits, boolean fair) {
        if (permits < 0) {
            throw new IllegalArgumentException("permits must be >= 0");
        }
        this.name = name;
        this.initialPermits = permits;
        this.availablePermits = permits;
        this.fair = fair;
        addHistory(name, "CREATE", permits);
        Trace.event("SEMAPHORE_CREATED",
                "Created semaphore '" + name + "' with " + permits
                        + " available permit(s), fair=" + fair,
                "Создан semaphore '" + name + "' с доступными permits: " + permits
                        + ", fair=" + fair,
                List.of(),
                state());
    }

    public boolean acquire(String thread) {
        return acquire(thread, 1);
    }

    public boolean acquire(String thread, int permits) {
        requirePermits(permits);
        if (canGrantImmediately(permits)) {
            grant(thread, permits);
            addHistory(thread, "ACQUIRE", permits);
            Trace.event("SEMAPHORE_ACQUIRE",
                    thread + " acquired " + permits + " permit(s); available="
                            + availablePermits,
                    thread + " получил permits: " + permits + "; доступно="
                            + availablePermits,
                    List.of("holder:" + thread, "available"),
                    state());
            return true;
        }

        waitingQueue.add(new Waiter(thread, permits));
        addHistory(thread, "WAIT", permits);
        Trace.event("SEMAPHORE_WAIT",
                thread + " needs " + permits + " permit(s), but only "
                        + availablePermits + " are available, so it waits in the queue",
                thread + " нужны permits: " + permits + ", но доступно только "
                        + availablePermits + ", поэтому он ждёт в очереди",
                List.of("queue:" + thread, "available"),
                state());
        return false;
    }

    public boolean tryAcquire(String thread) {
        return tryAcquire(thread, 1);
    }

    public boolean tryAcquire(String thread, int permits) {
        requirePermits(permits);
        if (availablePermits >= permits) {
            grant(thread, permits);
            addHistory(thread, "TRY_OK", permits);
            Trace.event("SEMAPHORE_TRY_ACQUIRE_SUCCESS",
                    thread + " used tryAcquire and immediately received "
                            + permits + " permit(s)",
                    thread + " вызвал tryAcquire и сразу получил permits: "
                            + permits,
                    List.of("holder:" + thread, "available"),
                    state());
            return true;
        }

        addHistory(thread, "TRY_FAIL", permits);
        Trace.event("SEMAPHORE_TRY_ACQUIRE_FAILED",
                thread + " used tryAcquire, found not enough permits, and did not join the queue",
                thread + " вызвал tryAcquire, не нашёл достаточно permits и не встал в очередь",
                List.of("available"),
                state());
        return false;
    }

    public void release(String thread) {
        release(thread, 1);
    }

    public void release(String thread, int permits) {
        requirePermits(permits);
        boolean knownHolder = removeHolder(thread, permits);
        availablePermits += permits;
        addHistory(thread, knownHolder ? "RELEASE" : "NO_OWNER_RELEASE", permits);

        if (knownHolder) {
            Trace.event("SEMAPHORE_RELEASE",
                    thread + " released " + permits + " permit(s); available="
                            + availablePermits,
                    thread + " освободил permits: " + permits + "; доступно="
                            + availablePermits,
                    List.of("available"),
                    state());
        } else {
            Trace.event("SEMAPHORE_NO_OWNER_RELEASE",
                    thread + " called release without being shown as a holder; Semaphore does not check ownership",
                    thread + " вызвал release, хотя не показан как holder; Semaphore не проверяет ownership",
                    List.of("available", "over"),
                    state());
        }

        grantWaitingThreads();
        if (trackedPermits() > initialPermits) {
            addHistory(thread, "OVER_RELEASE", trackedPermits() - initialPermits);
            Trace.event("SEMAPHORE_OVER_RELEASE",
                    "Tracked permits now exceed the initial " + initialPermits
                            + "; a misplaced release can increase capacity",
                    "Отслеживаемых permits стало больше начальных " + initialPermits
                            + "; лишний release может увеличить capacity",
                    List.of("available", "over"),
                    state());
        }
    }

    public int availablePermits() {
        return availablePermits;
    }

    public int queueLength() {
        return waitingQueue.size();
    }

    private boolean canGrantImmediately(int permits) {
        return availablePermits >= permits && (!fair || waitingQueue.isEmpty());
    }

    private void grant(String thread, int permits) {
        availablePermits -= permits;
        holders.merge(thread, permits, Integer::sum);
    }

    private boolean removeHolder(String thread, int permits) {
        Integer held = holders.get(thread);
        if (held == null) {
            return false;
        }
        if (held <= permits) {
            holders.remove(thread);
        } else {
            holders.put(thread, held - permits);
        }
        return true;
    }

    private void grantWaitingThreads() {
        while (!waitingQueue.isEmpty()) {
            Waiter next = waitingQueue.get(0);
            if (availablePermits < next.permits) {
                return;
            }
            waitingQueue.remove(0);
            grant(next.thread, next.permits);
            addHistory(next.thread, "GRANT", next.permits);
            Trace.event("SEMAPHORE_PERMIT_GRANTED",
                    "Released permit(s) were handed to waiting thread " + next.thread,
                    "Освобождённые permits переданы ожидающему thread " + next.thread,
                    List.of("holder:" + next.thread, "queue:" + next.thread, "available"),
                    state());
        }
    }

    private int trackedPermits() {
        int total = availablePermits;
        for (Integer permits : holders.values()) {
            total += permits;
        }
        return total;
    }

    private void requirePermits(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
    }

    private void addHistory(String actor, String action, int permits) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
        item.put("action", action);
        item.put("permits", permits);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("initialPermits", initialPermits);
        s.put("availablePermits", availablePermits);
        s.put("trackedPermits", trackedPermits());
        s.put("fair", fair);

        List<Object> holderList = new ArrayList<>();
        for (Map.Entry<String, Integer> e : holders.entrySet()) {
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("thread", e.getKey());
            holder.put("permits", e.getValue());
            holderList.add(holder);
        }
        s.put("holders", holderList);

        List<Object> queueList = new ArrayList<>();
        for (Waiter w : waitingQueue) {
            Map<String, Object> waiter = new LinkedHashMap<>();
            waiter.put("thread", w.thread);
            waiter.put("permits", w.permits);
            queueList.add(waiter);
        }
        s.put("waitingQueue", queueList);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static final class Waiter {
        final String thread;
        final int permits;

        Waiter(String thread, int permits) {
            this.thread = thread;
            this.permits = permits;
        }
    }
}
