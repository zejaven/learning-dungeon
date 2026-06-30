package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic teaching models for lock alternatives to synchronized.
 *
 * <p>The classes below do not block real Java threads. Examples name simulated
 * threads so traces can show queueing, reentrancy, shared reads and stamped
 * optimistic reads without depending on scheduler timing.
 */
public final class VisualLockAlternatives {

    private static final int HISTORY_LIMIT = 7;

    private VisualLockAlternatives() {
    }

    public static Reentrant reentrantLock(String name) {
        return new Reentrant(name, false);
    }

    public static Reentrant reentrantLock(String name, boolean fair) {
        return new Reentrant(name, fair);
    }

    public static ReadWrite readWriteLock(String name) {
        return new ReadWrite(name, true);
    }

    public static ReadWrite readWriteLock(String name, boolean fair) {
        return new ReadWrite(name, fair);
    }

    public static Stamped stampedLock(String name) {
        return new Stamped(name);
    }

    public static final class Reentrant {
        private final String name;
        private final boolean fair;
        private final List<String> waitingQueue = new ArrayList<>();
        private final List<Map<String, Object>> history = new ArrayList<>();

        private String owner;
        private int holdCount;

        private Reentrant(String name, boolean fair) {
            this.name = requireName(name);
            this.fair = fair;
            addHistory(history, name, "CREATE", fair ? "fair" : "nonfair");
            Trace.event("LOCK_CREATED",
                    "Created ReentrantLock '" + name + "'; use it when explicit tryLock, fairness or Conditions matter",
                    "Создан ReentrantLock '" + name + "'; выбирайте его, когда нужны явные tryLock, fairness или Conditions",
                    List.of(),
                    state());
        }

        public boolean lock(String thread) {
            thread = requireName(thread);
            if (owner == null) {
                owner = thread;
                holdCount = 1;
                addHistory(history, thread, "REENTRANT_ACQUIRE", "holdCount=1");
                Trace.event("REENTRANT_LOCK_ACQUIRED",
                        thread + " acquired the ReentrantLock and owns the critical section",
                        thread + " получил ReentrantLock и владеет critical section",
                        List.of("owner:" + thread),
                        state());
                return true;
            }
            if (owner.equals(thread)) {
                holdCount++;
                addHistory(history, thread, "REENTRANT_REENTER", "holdCount=" + holdCount);
                Trace.event("REENTRANT_LOCK_REENTERED",
                        thread + " entered the same ReentrantLock again; holdCount=" + holdCount,
                        thread + " повторно вошёл в тот же ReentrantLock; holdCount=" + holdCount,
                        List.of("owner:" + thread, "holdCount"),
                        state());
                return true;
            }

            enqueue(thread, "WRITE");
            addHistory(history, thread, "REENTRANT_WAIT", "owned by " + owner);
            Trace.event("REENTRANT_LOCK_WAITING",
                    thread + " waits because " + owner + " already owns the ReentrantLock",
                    thread + " ждёт, потому что " + owner + " уже владеет ReentrantLock",
                    List.of("queue:" + thread, "owner:" + owner),
                    state());
            return false;
        }

        public boolean tryLock(String thread) {
            thread = requireName(thread);
            if (owner == null || owner.equals(thread)) {
                return lock(thread);
            }

            addHistory(history, thread, "TRY_LOCK_FAIL", "owned by " + owner);
            Trace.event("REENTRANT_TRY_LOCK_FAILED",
                    thread + " used tryLock(), saw the lock was busy, and continued without waiting",
                    thread + " вызвал tryLock(), увидел занятый lock и продолжил без ожидания",
                    List.of("owner:" + owner),
                    state());
            return false;
        }

        public void unlock(String thread) {
            thread = requireName(thread);
            if (!thread.equals(owner)) {
                throw new IllegalStateException(thread + " does not own " + name);
            }

            holdCount--;
            if (holdCount > 0) {
                addHistory(history, thread, "REENTRANT_RELEASE", "holdCount=" + holdCount);
                Trace.event("REENTRANT_LOCK_RELEASED",
                        thread + " called unlock(), but the lock remains held until holdCount reaches zero",
                        thread + " вызвал unlock(), но lock остаётся удержанным, пока holdCount не станет нулём",
                        List.of("owner:" + thread, "holdCount"),
                        state());
                return;
            }

            owner = null;
            addHistory(history, thread, "REENTRANT_RELEASE", "free");
            Trace.event("REENTRANT_LOCK_RELEASED",
                    thread + " released the ReentrantLock; the next waiting thread can enter",
                    thread + " освободил ReentrantLock; следующий ожидающий thread может войти",
                    List.of(),
                    state());
            grantNextReentrant();
        }

        public int holdCount() {
            return holdCount;
        }

        public String owner() {
            return owner;
        }

        private void grantNextReentrant() {
            if (owner != null || waitingQueue.isEmpty()) {
                return;
            }
            String next = waitingQueue.remove(0);
            owner = next;
            holdCount = 1;
            addHistory(history, next, "REENTRANT_GRANT", "from queue");
            Trace.event("REENTRANT_LOCK_GRANTED",
                    "The released ReentrantLock was granted to waiting thread " + next,
                    "Освобождённый ReentrantLock передан ожидающему thread " + next,
                    List.of("owner:" + next),
                    state());
        }

        private void enqueue(String thread, String mode) {
            if (!waitingQueue.contains(thread)) {
                waitingQueue.add(thread);
            }
        }

        private Object state() {
            return baseState(name, "REENTRANT_LOCK",
                    fair ? "explicit exclusive lock, fair queue" : "explicit exclusive lock",
                    owner,
                    holdCount,
                    List.of(),
                    null,
                    queueFromNames(waitingQueue, "WRITE"),
                    0,
                    List.of(),
                    history);
        }
    }

    public static final class ReadWrite {
        private final String name;
        private final boolean fair;
        private final Map<String, Integer> readers = new LinkedHashMap<>();
        private final List<Request> waitingQueue = new ArrayList<>();
        private final List<Map<String, Object>> history = new ArrayList<>();

        private String writer;

        private ReadWrite(String name, boolean fair) {
            this.name = requireName(name);
            this.fair = fair;
            addHistory(history, name, "CREATE", fair ? "fair" : "nonfair");
            Trace.event("LOCK_CREATED",
                    "Created ReentrantReadWriteLock '" + name + "'; many readers may share it, but writers are exclusive",
                    "Создан ReentrantReadWriteLock '" + name + "'; несколько readers могут делить его, но writers эксклюзивны",
                    List.of(),
                    state());
        }

        public boolean readLock(String thread) {
            thread = requireName(thread);
            if (writer == null && (!fair || waitingQueue.isEmpty())) {
                readers.merge(thread, 1, Integer::sum);
                addHistory(history, thread, "READ_ACQUIRE", "readers=" + totalReaders());
                Trace.event("READWRITE_READ_SHARED",
                        thread + " acquired a read lock; read locks can be shared while no writer owns the lock",
                        thread + " получил read lock; read locks могут делиться, пока writer не владеет lock",
                        List.of("reader:" + thread),
                        state());
                return true;
            }

            enqueue(new Request(thread, "READ"));
            addHistory(history, thread, "READ_WAIT", writer == null ? "fair queue" : "writer=" + writer);
            Trace.event("READWRITE_READ_WAITING",
                    thread + " waits for a read lock because the write side or a fair queue is ahead",
                    thread + " ждёт read lock, потому что впереди write-сторона или fair queue",
                    List.of("queue:" + thread),
                    state());
            return false;
        }

        public boolean writeLock(String thread) {
            thread = requireName(thread);
            if (writer == null && readers.isEmpty()) {
                writer = thread;
                addHistory(history, thread, "WRITE_ACQUIRE", "exclusive");
                Trace.event("READWRITE_WRITE_ACQUIRED",
                        thread + " acquired the write lock; readers and other writers must wait",
                        thread + " получил write lock; readers и другие writers должны ждать",
                        List.of("writer:" + thread),
                        state());
                return true;
            }

            enqueue(new Request(thread, "WRITE"));
            addHistory(history, thread, "WRITE_WAIT", "readers=" + totalReaders());
            Trace.event("READWRITE_WRITE_WAITING",
                    thread + " waits for the write lock until all readers leave",
                    thread + " ждёт write lock, пока все readers не выйдут",
                    List.of("queue:" + thread),
                    state());
            return false;
        }

        public boolean upgradeToWriteLock(String thread) {
            thread = requireName(thread);
            if (readers.containsKey(thread)) {
                addHistory(history, thread, "UPGRADE_RISK", "read-to-write");
                Trace.event("READWRITE_UPGRADE_RISK",
                        thread + " already holds a read lock; upgrading to write can deadlock because it waits for readers including itself",
                        thread + " уже держит read lock; upgrade до write может зависнуть, потому что ждёт readers, включая себя",
                        List.of("reader:" + thread),
                        state());
                return false;
            }
            return writeLock(thread);
        }

        public void unlockRead(String thread) {
            thread = requireName(thread);
            Integer count = readers.get(thread);
            if (count == null) {
                throw new IllegalStateException(thread + " does not hold a read lock");
            }
            if (count == 1) {
                readers.remove(thread);
            } else {
                readers.put(thread, count - 1);
            }
            addHistory(history, thread, "READ_RELEASE", "readers=" + totalReaders());
            Trace.event("READWRITE_LOCK_RELEASED",
                    thread + " released a read lock",
                    thread + " освободил read lock",
                    List.of("reader:" + thread),
                    state());
            grantWaitingReadWrite();
        }

        public void unlockWrite(String thread) {
            thread = requireName(thread);
            if (!thread.equals(writer)) {
                throw new IllegalStateException(thread + " does not hold the write lock");
            }
            writer = null;
            addHistory(history, thread, "WRITE_RELEASE", "free");
            Trace.event("READWRITE_LOCK_RELEASED",
                    thread + " released the write lock",
                    thread + " освободил write lock",
                    List.of(),
                    state());
            grantWaitingReadWrite();
        }

        public int readerCount() {
            return totalReaders();
        }

        public String writer() {
            return writer;
        }

        private void grantWaitingReadWrite() {
            if (writer != null || waitingQueue.isEmpty()) {
                return;
            }
            Request first = waitingQueue.get(0);
            if ("WRITE".equals(first.mode)) {
                if (!readers.isEmpty()) {
                    return;
                }
                waitingQueue.remove(0);
                writer = first.thread;
                addHistory(history, first.thread, "WRITE_GRANT", "from queue");
                Trace.event("READWRITE_WRITE_ACQUIRED",
                        "The write lock was granted to waiting thread " + first.thread,
                        "Write lock передан ожидающему thread " + first.thread,
                        List.of("writer:" + first.thread),
                        state());
                return;
            }

            while (!waitingQueue.isEmpty() && "READ".equals(waitingQueue.get(0).mode) && writer == null) {
                Request next = waitingQueue.remove(0);
                readers.merge(next.thread, 1, Integer::sum);
                addHistory(history, next.thread, "READ_GRANT", "from queue");
                Trace.event("READWRITE_READ_SHARED",
                        "Read lock was granted to waiting thread " + next.thread,
                        "Read lock передан ожидающему thread " + next.thread,
                        List.of("reader:" + next.thread),
                        state());
            }
        }

        private void enqueue(Request request) {
            for (Request existing : waitingQueue) {
                if (existing.thread.equals(request.thread) && existing.mode.equals(request.mode)) {
                    return;
                }
            }
            waitingQueue.add(request);
        }

        private int totalReaders() {
            int total = 0;
            for (Integer count : readers.values()) {
                total += count;
            }
            return total;
        }

        private Object state() {
            return baseState(name, "READ_WRITE_LOCK",
                    "shared read lock, exclusive write lock",
                    null,
                    0,
                    readersFromMap(readers),
                    writer,
                    queueFromRequests(waitingQueue),
                    0,
                    List.of(),
                    history);
        }
    }

    public static final class Stamped {
        private final String name;
        private final Map<String, Integer> readers = new LinkedHashMap<>();
        private final List<Request> waitingQueue = new ArrayList<>();
        private final List<OptimisticRead> optimisticReads = new ArrayList<>();
        private final List<Map<String, Object>> history = new ArrayList<>();

        private String writer;
        private long version;

        private Stamped(String name) {
            this.name = requireName(name);
            addHistory(history, name, "CREATE", "version=0");
            Trace.event("LOCK_CREATED",
                    "Created StampedLock '" + name + "'; optimistic reads must be validated after reading",
                    "Создан StampedLock '" + name + "'; optimistic reads нужно validate после чтения",
                    List.of(),
                    state());
        }

        public long tryOptimisticRead(String thread) {
            thread = requireName(thread);
            long stamp = version;
            optimisticReads.add(new OptimisticRead(thread, stamp));
            addHistory(history, thread, "OPTIMISTIC_READ", "stamp=" + stamp);
            Trace.event("STAMPED_OPTIMISTIC_READ",
                    thread + " took an optimistic read stamp " + stamp + " without blocking writers",
                    thread + " получил optimistic read stamp " + stamp + " без блокировки writers",
                    List.of("optimistic:" + thread),
                    state());
            return stamp;
        }

        public boolean validate(String thread, long stamp) {
            thread = requireName(thread);
            boolean valid = stamp == version && writer == null;
            addHistory(history, thread, valid ? "VALIDATE_OK" : "VALIDATE_FAIL", "stamp=" + stamp);
            Trace.event(valid ? "STAMPED_VALIDATE_OK" : "STAMPED_VALIDATE_FAILED",
                    valid
                            ? thread + " validated stamp " + stamp + "; no write changed the data"
                            : thread + " validated stamp " + stamp + "; a write changed the data, so the read must retry",
                    valid
                            ? thread + " проверил stamp " + stamp + "; write не менял данные"
                            : thread + " проверил stamp " + stamp + "; write изменил данные, поэтому чтение нужно повторить",
                    List.of("optimistic:" + thread, "version"),
                    state());
            return valid;
        }

        public long readLock(String thread) {
            thread = requireName(thread);
            if (writer == null && waitingQueue.isEmpty()) {
                readers.merge(thread, 1, Integer::sum);
                addHistory(history, thread, "STAMPED_READ", "readers=" + totalReaders());
                Trace.event("STAMPED_READ_LOCK",
                        thread + " acquired a pessimistic read stamp",
                        thread + " получил pessimistic read stamp",
                        List.of("reader:" + thread),
                        state());
                return version;
            }

            waitingQueue.add(new Request(thread, "READ"));
            addHistory(history, thread, "STAMPED_READ_WAIT", "writer=" + writer);
            Trace.event("STAMPED_READ_WAITING",
                    thread + " waits for a read stamp because a writer is active or queued first",
                    thread + " ждёт read stamp, потому что writer активен или стоит первым в очереди",
                    List.of("queue:" + thread),
                    state());
            return 0;
        }

        public long writeLock(String thread) {
            thread = requireName(thread);
            if (writer == null && readers.isEmpty()) {
                writer = thread;
                addHistory(history, thread, "STAMPED_WRITE", "exclusive");
                Trace.event("STAMPED_WRITE_LOCK",
                        thread + " acquired a write stamp; optimistic readers must validate before trusting old values",
                        thread + " получил write stamp; optimistic readers должны validate, прежде чем доверять старым значениям",
                        List.of("writer:" + thread),
                        state());
                return version + 1;
            }

            waitingQueue.add(new Request(thread, "WRITE"));
            addHistory(history, thread, "STAMPED_WRITE_WAIT", "readers=" + totalReaders());
            Trace.event("STAMPED_WRITE_WAITING",
                    thread + " waits for a write stamp until readers leave",
                    thread + " ждёт write stamp, пока readers не выйдут",
                    List.of("queue:" + thread),
                    state());
            return 0;
        }

        public long tryConvertToWriteLock(String thread) {
            thread = requireName(thread);
            Integer ownReads = readers.get(thread);
            if (writer == null && ownReads != null && ownReads == 1 && totalReaders() == 1) {
                readers.remove(thread);
                writer = thread;
                addHistory(history, thread, "CONVERT_TO_WRITE", "success");
                Trace.event("STAMPED_CONVERT_TO_WRITE",
                        thread + " converted the only read stamp into a write stamp",
                        thread + " преобразовал единственный read stamp в write stamp",
                        List.of("writer:" + thread),
                        state());
                return version + 1;
            }

            addHistory(history, thread, "CONVERT_FAIL", "other readers or no read stamp");
            Trace.event("STAMPED_CONVERT_FAILED",
                    thread + " could not convert to write because another reader exists or no read stamp is held",
                    thread + " не смог convert to write, потому что есть другой reader или read stamp не удерживается",
                    List.of("reader:" + thread),
                    state());
            return 0;
        }

        public void unlockRead(String thread) {
            thread = requireName(thread);
            Integer count = readers.get(thread);
            if (count == null) {
                throw new IllegalStateException(thread + " does not hold a read stamp");
            }
            if (count == 1) {
                readers.remove(thread);
            } else {
                readers.put(thread, count - 1);
            }
            addHistory(history, thread, "STAMPED_READ_RELEASE", "readers=" + totalReaders());
            Trace.event("STAMPED_UNLOCK_READ",
                    thread + " released a pessimistic read stamp",
                    thread + " освободил pessimistic read stamp",
                    List.of("reader:" + thread),
                    state());
            grantWaitingStamped();
        }

        public void unlockWrite(String thread) {
            thread = requireName(thread);
            if (!thread.equals(writer)) {
                throw new IllegalStateException(thread + " does not hold a write stamp");
            }
            writer = null;
            version++;
            addHistory(history, thread, "STAMPED_WRITE_RELEASE", "version=" + version);
            Trace.event("STAMPED_UNLOCK_WRITE",
                    thread + " released the write stamp; version is now " + version,
                    thread + " освободил write stamp; version теперь " + version,
                    List.of("version"),
                    state());
            grantWaitingStamped();
        }

        public long version() {
            return version;
        }

        private void grantWaitingStamped() {
            if (writer != null || waitingQueue.isEmpty()) {
                return;
            }
            Request first = waitingQueue.get(0);
            if ("WRITE".equals(first.mode)) {
                if (!readers.isEmpty()) {
                    return;
                }
                waitingQueue.remove(0);
                writer = first.thread;
                addHistory(history, first.thread, "STAMPED_WRITE_GRANT", "from queue");
                Trace.event("STAMPED_WRITE_LOCK",
                        "Write stamp was granted to waiting thread " + first.thread,
                        "Write stamp передан ожидающему thread " + first.thread,
                        List.of("writer:" + first.thread),
                        state());
                return;
            }

            while (!waitingQueue.isEmpty() && "READ".equals(waitingQueue.get(0).mode) && writer == null) {
                Request next = waitingQueue.remove(0);
                readers.merge(next.thread, 1, Integer::sum);
                addHistory(history, next.thread, "STAMPED_READ_GRANT", "from queue");
                Trace.event("STAMPED_READ_LOCK",
                        "Read stamp was granted to waiting thread " + next.thread,
                        "Read stamp передан ожидающему thread " + next.thread,
                        List.of("reader:" + next.thread),
                        state());
            }
        }

        private int totalReaders() {
            int total = 0;
            for (Integer count : readers.values()) {
                total += count;
            }
            return total;
        }

        private Object state() {
            return baseState(name, "STAMPED_LOCK",
                    "optimistic read stamps plus read/write modes",
                    null,
                    0,
                    readersFromMap(readers),
                    writer,
                    queueFromRequests(waitingQueue),
                    version,
                    optimisticFromList(optimisticReads, version, writer),
                    history);
        }
    }

    private static Object baseState(String name, String kind, String policy, String owner,
                                    int holdCount, Object readers, String writer,
                                    Object waitingQueue, long version,
                                    Object optimisticReads,
                                    List<Map<String, Object>> history) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("kind", kind);
        s.put("policy", policy);
        s.put("owner", owner);
        s.put("holdCount", holdCount);
        s.put("readers", readers);
        s.put("writer", writer);
        s.put("waitingQueue", waitingQueue);
        s.put("version", version);
        s.put("optimisticReads", optimisticReads);
        s.put("history", new ArrayList<>(history));
        return s;
    }

    private static List<Object> readersFromMap(Map<String, Integer> readers) {
        List<Object> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : readers.entrySet()) {
            Map<String, Object> reader = new LinkedHashMap<>();
            reader.put("thread", e.getKey());
            reader.put("holds", e.getValue());
            list.add(reader);
        }
        return list;
    }

    private static List<Object> queueFromNames(List<String> queue, String mode) {
        List<Object> list = new ArrayList<>();
        for (String thread : queue) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("thread", thread);
            item.put("mode", mode);
            list.add(item);
        }
        return list;
    }

    private static List<Object> queueFromRequests(List<Request> queue) {
        List<Object> list = new ArrayList<>();
        for (Request request : queue) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("thread", request.thread);
            item.put("mode", request.mode);
            list.add(item);
        }
        return list;
    }

    private static List<Object> optimisticFromList(List<OptimisticRead> optimisticReads,
                                                   long version, String writer) {
        List<Object> list = new ArrayList<>();
        for (OptimisticRead read : optimisticReads) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("thread", read.thread);
            item.put("stamp", read.stamp);
            item.put("valid", read.stamp == version && writer == null);
            list.add(item);
        }
        return list;
    }

    private static void addHistory(List<Map<String, Object>> history,
                                   String actor, String action, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("actor", actor);
        item.put("action", action);
        item.put("detail", detail);
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return value;
    }

    private static final class Request {
        final String thread;
        final String mode;

        Request(String thread, String mode) {
            this.thread = thread;
            this.mode = mode;
        }
    }

    private static final class OptimisticRead {
        final String thread;
        final long stamp;

        OptimisticRead(String thread, long stamp) {
            this.thread = thread;
            this.stamp = stamp;
        }
    }
}
