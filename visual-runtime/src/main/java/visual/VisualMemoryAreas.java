package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of <strong>the runtime data areas of a JVM instance</strong> — the
 * answer to "tell us about memory structures in Java, and how many stacks and heaps does an
 * application have?".
 *
 * <p>The mental model interviewers want is a split by <em>scope</em>:
 * <ul>
 *   <li><b>Shared — exactly one per JVM instance:</b> the heap (all objects and arrays, managed by
 *       the garbage collector), the string pool (inside the heap since Java 7), Metaspace (class
 *       metadata and runtime constant pools, in native memory since Java 8) and the code cache
 *       (native code produced by the JIT).</li>
 *   <li><b>Per thread — one each per live thread:</b> the JVM stack of frames, the PC register and
 *       the native method stack.</li>
 * </ul>
 * So the count of heaps is always 1 no matter how many threads run, while the count of stacks
 * equals the number of live threads and rises and falls as threads start and finish.
 *
 * <p>This is NOT a real JVM. It keeps a list of items per shared area and a small per-thread record
 * (frames, a program-counter offset, native frames), and never starts real threads — it simulates
 * them — so an example runs deterministically while showing exactly where each piece of data lands.
 * Real details deliberately left out: generations inside the heap, thread-local allocation buffers,
 * class unloading, and code-cache eviction.
 *
 * <p>It emits {@link Trace} events so the UI can draw the shared areas filling up while the
 * per-thread trio appears and disappears with each thread.
 */
public class VisualMemoryAreas {

    /** The shared areas, in display order. Each of these exists exactly ONCE per JVM instance. */
    private static final List<String> SHARED_AREAS = List.of("heap", "string-pool", "metaspace", "code-cache");
    /** Areas that physically live inside the heap (the string pool does, since Java 7). */
    private static final List<String> INSIDE_HEAP = List.of("string-pool");
    /** How many areas every single thread owns privately: JVM stack, PC register, native stack. */
    private static final int PER_THREAD_AREAS = 3;

    /** Shared area id -> its items, in insertion order. */
    private final Map<String, List<Map<String, Object>>> shared = new LinkedHashMap<>();
    /** Live threads, in start order: thread name -> its private areas. */
    private final Map<String, ThreadState> threads = new LinkedHashMap<>();

    private int itemSeq;

    /**
     * Boots a JVM instance: the four shared areas exist once and start empty, and the bootstrap
     * {@code main} thread gets its own stack, PC register and native method stack.
     */
    public VisualMemoryAreas() {
        for (String area : SHARED_AREAS) {
            shared.put(area, new ArrayList<>());
        }
        threads.put("main", new ThreadState("main"));
        Trace.event("AREAS_SCENE",
                "One JVM instance boots: the shared areas (heap, string pool, Metaspace, code cache) exist "
                        + "once each; the main thread gets its own stack, PC register and native method stack",
                "Стартует один инстанс JVM: общие области (куча, пул строк, Metaspace, кэш кода) существуют "
                        + "по одной; поток main получает свои стек, PC-регистр и стек нативных методов",
                List.of("area:heap", "thread:main"), state());
    }

    /**
     * Models a class being loaded: its metadata (fields, methods, runtime constant pool) goes to
     * Metaspace — native memory outside the heap, not counted in {@code -Xmx}.
     */
    public void loadClass(String className) {
        String id = add("metaspace", className + ".class", null);
        Trace.event("CLASS_LOADED",
                "Class " + className + " is loaded — its metadata (fields, methods, runtime constant pool) "
                        + "goes to Metaspace, which lives in native memory OUTSIDE the heap",
                "Класс " + className + " загружается — его метаданные (поля, методы, runtime constant pool) "
                        + "попадают в Metaspace, который живёт в нативной памяти ВНЕ кучи",
                List.of("area:metaspace", "item:" + id), state());
    }

    /**
     * Models {@code new} executed by a thread: whichever thread runs it, the object itself lands in
     * the one shared heap; only the reference to it sits in that thread's frame.
     */
    public String allocate(String thread, String type) {
        String id = add("heap", type, thread);
        advance(thread);
        Trace.event("OBJECT_ALLOCATED",
                "Thread " + thread + " runs new " + type + "() — the object goes to the ONE shared heap; "
                        + "only the reference to it sits in " + thread + "'s frame",
                "Поток " + thread + " выполняет new " + type + "() — объект попадает в ЕДИНСТВЕННУЮ общую "
                        + "кучу; в кадре потока " + thread + " лежит только ссылка на него",
                List.of("area:heap", "item:" + id, "thread:" + thread), state());
        return id;
    }

    /**
     * Models interning a string literal: it is stored in the string pool, which since Java 7 is a
     * table inside the heap (it used to live in PermGen).
     */
    public void internString(String thread, String literal) {
        String label = "\"" + literal + "\"";
        String existing = findLabel("string-pool", label);
        if (existing != null) {
            advance(thread);
            Trace.event("STRING_INTERNED",
                    "Literal " + label + " is already in the string pool — no new object is created, the same "
                            + "pooled instance is reused",
                    "Литерал " + label + " уже есть в пуле строк — новый объект не создаётся, переиспользуется "
                            + "тот же экземпляр из пула",
                    List.of("area:string-pool", "item:" + existing, "thread:" + thread), state());
            return;
        }
        String id = add("string-pool", label, thread);
        advance(thread);
        Trace.event("STRING_INTERNED",
                "Literal " + label + " is interned — it is stored in the string pool, which since Java 7 sits "
                        + "INSIDE the heap (it used to live in PermGen)",
                "Литерал " + label + " интернируется — он попадает в пул строк, который с Java 7 находится "
                        + "ВНУТРИ кучи (раньше он жил в PermGen)",
                List.of("area:string-pool", "item:" + id, "thread:" + thread), state());
    }

    /**
     * Models the JIT compiling a hot method: the machine code it produces is stored in the code
     * cache — a separate area, neither the heap nor Metaspace.
     */
    public void jitCompile(String method) {
        String id = add("code-cache", method + "()", null);
        Trace.event("JIT_COMPILED",
                "Method " + method + "() got hot — the JIT compiles it to native code kept in the code cache, "
                        + "a separate area that is neither the heap nor Metaspace",
                "Метод " + method + "() стал горячим — JIT компилирует его в нативный код, который хранится "
                        + "в кэше кода: это отдельная область, не куча и не Metaspace",
                List.of("area:code-cache", "item:" + id), state());
    }

    /**
     * Models a thread starting: it gets its own stack, PC register and native method stack. The
     * shared areas stay at one each.
     */
    public void startThread(String name) {
        if (threads.containsKey(name)) {
            return;
        }
        threads.put(name, new ThreadState("run"));
        Trace.event("THREAD_STARTED",
                "Thread " + name + " starts — it gets its OWN stack, PC register and native method stack; now "
                        + threads.size() + " of each, while every shared area is still exactly one",
                "Поток " + name + " стартует — он получает СВОИ стек, PC-регистр и стек нативных методов; "
                        + "теперь их по " + threads.size() + ", а каждая общая область по-прежнему одна",
                List.of("thread:" + name, "pc:" + name), state());
    }

    /** Models a method call on a thread: one frame is pushed onto that thread's own stack. */
    public void call(String thread, String method) {
        ThreadState t = threads.get(thread);
        if (t == null) {
            return;
        }
        t.frames.add(new Frame(method));
        int depth = t.frames.size() - 1;
        Trace.event("FRAME_PUSHED",
                "Thread " + thread + " calls " + method + "() — a frame with its local variables is pushed on "
                        + thread + "'s own stack, and " + thread + "'s PC register now points inside " + method + "()",
                "Поток " + thread + " вызывает " + method + "() — на его собственный стек кладётся кадр с "
                        + "локальными переменными, а PC-регистр потока " + thread + " указывает внутрь " + method + "()",
                List.of("frame:" + thread + ":" + depth, "pc:" + thread), state());
    }

    /** Models returning from the current method on a thread: its top frame is popped. */
    public void ret(String thread) {
        ThreadState t = threads.get(thread);
        if (t == null || t.frames.size() <= 1) {
            return;
        }
        Frame gone = t.frames.remove(t.frames.size() - 1);
        advance(thread);
        Trace.event("FRAME_POPPED",
                "Thread " + thread + " returns from " + gone.method + "() — its frame is popped and its locals "
                        + "are gone; the PC register goes back to the caller",
                "Поток " + thread + " возвращается из " + gone.method + "() — его кадр снимается вместе с "
                        + "локальными переменными; PC-регистр возвращается к вызывающему",
                List.of("thread:" + thread, "pc:" + thread), state());
    }

    /**
     * Models calling a {@code native} method: the call is recorded on that thread's native method
     * stack, a separate per-thread area from its JVM stack.
     */
    public void callNative(String thread, String method) {
        ThreadState t = threads.get(thread);
        if (t == null) {
            return;
        }
        t.nativeFrames.add(method);
        advance(thread);
        int depth = t.nativeFrames.size() - 1;
        Trace.event("NATIVE_CALL",
                "Thread " + thread + " calls the native method " + method + "() — the call is recorded on that "
                        + "thread's NATIVE method stack, a separate area from its JVM stack",
                "Поток " + thread + " вызывает нативный метод " + method + "() — вызов попадает на НАТИВНЫЙ "
                        + "стек методов этого потока, отдельный от его JVM-стека",
                List.of("native:" + thread + ":" + depth, "thread:" + thread), state());
    }

    /**
     * Models a thread finishing: its three private areas are discarded. The shared areas and
     * everything in them remain — objects it allocated stay in the heap until the GC reclaims them.
     */
    public void endThread(String name) {
        if ("main".equals(name) || !threads.containsKey(name)) {
            return;
        }
        threads.remove(name);
        Trace.event("THREAD_EXITED",
                "Thread " + name + " finishes — its stack, PC register and native method stack all disappear; "
                        + "the shared areas and everything in them remain",
                "Поток " + name + " завершается — его стек, PC-регистр и стек нативных методов исчезают; "
                        + "общие области и всё их содержимое остаются",
                List.of("thread:" + name, "area:heap"), state());
    }

    /**
     * Emits the answer to the counting question: the shared areas exist once per JVM instance, the
     * private ones once per live thread.
     */
    public void countAreas() {
        int t = threads.size();
        Trace.event("AREA_COUNT",
                "Counting for this JVM instance: 1 heap, 1 string pool (inside it), 1 Metaspace, 1 code cache — "
                        + "shared by everyone; " + t + " live thread(s) means " + t + " stack(s), " + t
                        + " PC register(s) and " + t + " native method stack(s)",
                "Считаем для этого инстанса JVM: 1 куча, 1 пул строк (внутри неё), 1 Metaspace, 1 кэш кода — "
                        + "общие для всех; " + t + " живых потока(ов) означает " + t + " стек(ов), " + t
                        + " PC-регистр(ов) и " + t + " стек(ов) нативных методов",
                List.of("area:heap", "area:metaspace", "area:code-cache", "area:string-pool"), state());
    }

    /** Number of live threads — and therefore of stacks, PC registers and native method stacks. */
    public int threadCount() {
        return threads.size();
    }

    /** Number of areas shared by the whole JVM instance, whatever the thread count is. */
    public int sharedAreaCount() {
        return SHARED_AREAS.size();
    }

    // --- internals -------------------------------------------------------

    /** Adds one item to a shared area and returns its stable id. */
    private String add(String area, String label, String owner) {
        String id = "i" + (++itemSeq);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("label", label);
        item.put("owner", owner);
        shared.get(area).add(item);
        return id;
    }

    /** Id of an item already stored in an area under this label, or null. */
    private String findLabel(String area, String label) {
        for (Map<String, Object> item : shared.get(area)) {
            if (label.equals(item.get("label"))) {
                return String.valueOf(item.get("id"));
            }
        }
        return null;
    }

    /** Moves a thread's program counter one instruction on inside its current method. */
    private void advance(String thread) {
        ThreadState t = threads.get(thread);
        if (t != null && !t.frames.isEmpty()) {
            t.frames.get(t.frames.size() - 1).pc++;
        }
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("threadCount", threads.size());
        s.put("sharedAreaCount", SHARED_AREAS.size());
        s.put("perThreadAreaCount", PER_THREAD_AREAS);

        List<Object> areas = new ArrayList<>();
        for (String area : SHARED_AREAS) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", area);
            a.put("insideHeap", INSIDE_HEAP.contains(area));
            List<Object> items = new ArrayList<>();
            for (Map<String, Object> item : shared.get(area)) {
                items.add(new LinkedHashMap<>(item));
            }
            a.put("items", items);
            areas.add(a);
        }
        s.put("shared", areas);

        List<Object> threadList = new ArrayList<>();
        for (Map.Entry<String, ThreadState> e : threads.entrySet()) {
            ThreadState t = e.getValue();
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("name", e.getKey());
            tm.put("pc", t.pcLabel());
            List<Object> frames = new ArrayList<>();
            for (int i = 0; i < t.frames.size(); i++) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("depth", i);
                fm.put("name", t.frames.get(i).method);
                frames.add(fm);
            }
            tm.put("frames", frames);
            List<Object> natives = new ArrayList<>();
            for (int i = 0; i < t.nativeFrames.size(); i++) {
                Map<String, Object> nm = new LinkedHashMap<>();
                nm.put("depth", i);
                nm.put("name", t.nativeFrames.get(i));
                natives.add(nm);
            }
            tm.put("nativeFrames", natives);
            threadList.add(tm);
        }
        s.put("threads", threadList);
        return s;
    }

    /** The three areas a single thread owns privately. */
    private static final class ThreadState {
        final List<Frame> frames = new ArrayList<>();
        final List<String> nativeFrames = new ArrayList<>();

        ThreadState(String bottomFrame) {
            frames.add(new Frame(bottomFrame));
        }

        /** What this thread's PC register holds: an offset inside the method it is executing. */
        String pcLabel() {
            if (frames.isEmpty()) {
                return "-";
            }
            Frame top = frames.get(frames.size() - 1);
            return top.method + " @ " + top.pc;
        }
    }

    /** One stack frame: the method it belongs to plus this thread's position inside it. */
    private static final class Frame {
        final String method;
        int pc;

        Frame(String method) {
            this.method = method;
        }
    }
}
