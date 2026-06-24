package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deterministic teaching model of Singleton creation. It does not start real
 * Java threads; examples name simulated threads so the trace can show unsafe
 * lazy creation, synchronized access, double-checked locking with volatile, and
 * enum singleton access without relying on scheduler timing.
 */
public class VisualSingleton {

    private static final int HISTORY_LIMIT = 8;

    private final String name;
    private final String strategy;
    private final boolean volatileField;
    private final boolean enumBased;
    private final Map<String, ThreadState> threads = new LinkedHashMap<>();
    private final List<Map<String, Object>> history = new ArrayList<>();

    private String instance;
    private String lockOwner;
    private int constructorCalls;

    private VisualSingleton(String name, String strategy, boolean volatileField, boolean enumBased) {
        this.name = name;
        this.strategy = strategy;
        this.volatileField = volatileField;
        this.enumBased = enumBased;
        Trace.event("SINGLETON_MODEL_CREATED",
                "Created Singleton model '" + name + "' using " + strategy,
                "Создана модель Singleton '" + name + "' со стратегией " + strategy,
                List.of(),
                state());

        if (enumBased) {
            constructorCalls = 1;
            instance = name + ".INSTANCE";
            addHistory("JVM", "ENUM_INIT", instance);
            Trace.event("SINGLETON_ENUM_READY",
                    "The enum constant is initialized by the JVM exactly once",
                    "Enum-константа инициализируется JVM ровно один раз",
                    List.of("instance"),
                    state());
        }
    }

    public static VisualSingleton unsafeLazy(String name) {
        return new VisualSingleton(name, "UNSAFE_LAZY", false, false);
    }

    public static VisualSingleton synchronizedLazy(String name) {
        return new VisualSingleton(name, "SYNCHRONIZED_LAZY", false, false);
    }

    public static VisualSingleton doubleCheckedLocking(String name) {
        return new VisualSingleton(name, "DOUBLE_CHECKED_LOCKING", true, false);
    }

    public static VisualSingleton enumSingleton(String name) {
        return new VisualSingleton(name, "ENUM_SINGLETON", false, true);
    }

    public void unsafeGet(String thread) {
        ThreadState t = thread(thread);
        t.status = "CHECKING";
        t.observedInstance = instance == null ? "null" : instance;
        addHistory(thread, "UNSAFE_CHECK", t.observedInstance);
        Trace.event("SINGLETON_UNSAFE_CHECK",
                thread + " checks the static field without a lock and sees " + t.observedInstance,
                thread + " проверяет static поле без lock и видит " + t.observedInstance,
                List.of("thread:" + thread, "instance"),
                state());

        if (instance == null) {
            createInstance(thread, "SINGLETON_INSTANCE_CREATED",
                    thread + " constructs the Singleton because the field was null",
                    thread + " создаёт Singleton, потому что поле было null");
        } else {
            reuse(thread);
        }
    }

    public void unsafeRace(String firstThread, String secondThread) {
        checkNullWithoutLock(firstThread);
        checkNullWithoutLock(secondThread);
        createInstance(firstThread, "SINGLETON_INSTANCE_CREATED",
                firstThread + " constructs the first Singleton instance",
                firstThread + " создаёт первый экземпляр Singleton");
        createInstance(secondThread, "SINGLETON_DUPLICATE_CREATED",
                "Race: " + secondThread + " also constructs an instance from its stale null check",
                "Race: " + secondThread + " тоже создаёт экземпляр из-за устаревшей проверки null");
    }

    public void synchronizedGet(String thread) {
        ThreadState t = thread(thread);
        lockOwner = thread;
        t.status = "IN_SYNCHRONIZED_METHOD";
        addHistory(thread, "ENTER_LOCK", null);
        Trace.event("SINGLETON_SYNCHRONIZED_LOCK",
                thread + " enters the synchronized getInstance() method and owns the class lock",
                thread + " входит в synchronized getInstance() и владеет class lock",
                List.of("thread:" + thread, "lock"),
                state());

        if (instance == null) {
            createInstance(thread, "SINGLETON_INSTANCE_CREATED",
                    thread + " constructs the instance while holding the lock",
                    thread + " создаёт экземпляр, пока владеет lock");
        } else {
            reuse(thread);
        }

        lockOwner = null;
        t.status = "DONE";
        addHistory(thread, "EXIT_LOCK", null);
        Trace.event("SINGLETON_LOCK_RELEASED",
                thread + " exits getInstance() and releases the class lock",
                thread + " выходит из getInstance() и освобождает class lock",
                List.of("thread:" + thread, "lock"),
                state());
    }

    public void doubleCheckedGet(String thread) {
        ThreadState t = thread(thread);
        t.status = "FIRST_CHECK";
        t.observedInstance = instance == null ? "null" : instance;
        addHistory(thread, "DCL_FIRST_CHECK", t.observedInstance);
        Trace.event("SINGLETON_DCL_FIRST_CHECK",
                thread + " performs the first null check before taking the lock and sees " + t.observedInstance,
                thread + " выполняет первую проверку null до lock и видит " + t.observedInstance,
                List.of("thread:" + thread, "instance"),
                state());

        if (instance != null) {
            reuse(thread);
            return;
        }

        lockOwner = thread;
        t.status = "IN_DCL_LOCK";
        addHistory(thread, "ENTER_LOCK", null);
        Trace.event("SINGLETON_SYNCHRONIZED_LOCK",
                thread + " enters the synchronized block for the slow path",
                thread + " входит в synchronized block для медленного пути",
                List.of("thread:" + thread, "lock"),
                state());

        t.status = "SECOND_CHECK";
        t.observedInstance = instance == null ? "null" : instance;
        addHistory(thread, "DCL_SECOND_CHECK", t.observedInstance);
        Trace.event("SINGLETON_DCL_SECOND_CHECK",
                thread + " checks again inside the lock before constructing",
                thread + " проверяет поле ещё раз внутри lock перед созданием",
                List.of("thread:" + thread, "lock", "instance"),
                state());

        if (instance == null) {
            createInstance(thread, "SINGLETON_INSTANCE_CREATED",
                    thread + " constructs the instance after the second check",
                    thread + " создаёт экземпляр после второй проверки");
            addHistory(thread, "VOLATILE_PUBLISH", instance);
            Trace.event("SINGLETON_VOLATILE_PUBLISH",
                    "The volatile field publishes the fully constructed instance to other threads",
                    "Поле volatile публикует полностью созданный экземпляр для других threads",
                    List.of("thread:" + thread, "instance"),
                    state());
        } else {
            reuse(thread);
        }

        lockOwner = null;
        t.status = "DONE";
        addHistory(thread, "EXIT_LOCK", null);
        Trace.event("SINGLETON_LOCK_RELEASED",
                thread + " exits the synchronized block",
                thread + " выходит из synchronized block",
                List.of("thread:" + thread, "lock"),
                state());
    }

    public void enumAccess(String thread) {
        if (!enumBased) {
            throw new IllegalStateException("enumAccess() is only valid for enumSingleton()");
        }
        ThreadState t = thread(thread);
        t.status = "ENUM_ACCESS";
        t.observedInstance = instance;
        addHistory(thread, "ENUM_ACCESS", instance);
        Trace.event("SINGLETON_ENUM_ACCESS",
                thread + " reads the already initialized enum constant " + instance,
                thread + " читает уже инициализированную enum-константу " + instance,
                List.of("thread:" + thread, "instance"),
                state());
    }

    public String instance() {
        return instance;
    }

    public int constructorCalls() {
        return constructorCalls;
    }

    private void checkNullWithoutLock(String thread) {
        ThreadState t = thread(thread);
        t.status = "CHECKED_NULL";
        t.observedInstance = "null";
        addHistory(thread, "UNSAFE_CHECK", "null");
        Trace.event("SINGLETON_UNSAFE_CHECK",
                thread + " checks without a lock and sees null",
                thread + " проверяет без lock и видит null",
                List.of("thread:" + thread, "instance"),
                state());
    }

    private void createInstance(String thread, String event, String descEn, String descRu) {
        constructorCalls++;
        instance = name + "#" + constructorCalls;
        ThreadState t = thread(thread);
        t.status = "CONSTRUCTED";
        t.observedInstance = instance;
        addHistory(thread, "CONSTRUCT", instance);
        Trace.event(event, descEn, descRu,
                List.of("thread:" + thread, "instance"),
                state());
    }

    private void reuse(String thread) {
        ThreadState t = thread(thread);
        t.status = "REUSED";
        t.observedInstance = instance;
        addHistory(thread, "REUSE", instance);
        Trace.event("SINGLETON_INSTANCE_REUSED",
                thread + " reuses existing instance " + instance,
                thread + " переиспользует существующий экземпляр " + instance,
                List.of("thread:" + thread, "instance"),
                state());
    }

    private ThreadState thread(String name) {
        return threads.computeIfAbsent(name, ThreadState::new);
    }

    private void addHistory(String thread, String action, String value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("thread", thread);
        item.put("action", action);
        if (value != null) {
            item.put("value", value);
        }
        history.add(item);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("strategy", strategy);
        s.put("instance", instance);
        s.put("constructorCalls", constructorCalls);
        s.put("lockOwner", lockOwner);
        s.put("volatileField", volatileField);
        s.put("enumBased", enumBased);

        List<Object> threadList = new ArrayList<>();
        for (ThreadState t : threads.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", t.name);
            item.put("status", t.status);
            if (t.observedInstance != null) {
                item.put("observedInstance", t.observedInstance);
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
        String observedInstance;

        ThreadState(String name) {
            this.name = name;
        }
    }
}
