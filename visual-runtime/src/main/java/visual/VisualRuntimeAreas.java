package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> that answers the interview question
 * <strong>"how many heaps and how many stacks are there in one JVM instance?"</strong>
 *
 * <p>The mental model interviewers want: a single JVM instance has exactly <em>one</em> heap,
 * shared by every thread and managed by the garbage collector, and <em>one stack per thread</em>
 * (plus a per-thread PC register and native stack, not modelled here). So the heap count is always
 * {@code 1}; the stack count equals the number of live threads and grows and shrinks as threads
 * start and finish.
 *
 * <p>This is NOT a real JVM. It tracks a list of heap objects (each remembering which thread ran
 * {@code new}, only to show that the object still lands in the one shared heap) and a map of
 * per-thread frame stacks. It never starts real threads — it simulates them — so an example runs
 * deterministically while showing exactly how the areas are counted.
 *
 * <p>It emits {@link Trace} events so the UI can draw the single heap and the per-thread stacks
 * appearing and disappearing.
 */
public class VisualRuntimeAreas {

    /** Per-thread call stacks: thread name -> its frames, bottom frame first. Insertion-ordered. */
    private final Map<String, List<String>> stacks = new LinkedHashMap<>();
    /** The single shared heap: every object allocated by any thread lives here. */
    private final List<Map<String, Object>> heap = new ArrayList<>();
    /** Sequence for stable object ids (o1, o2, ...). */
    private int objectSeq;

    /**
     * Boots a JVM instance with its bootstrap {@code main} thread: one stack holding the
     * {@code main()} frame, and one empty heap.
     */
    public VisualRuntimeAreas() {
        List<String> mainFrames = new ArrayList<>();
        mainFrames.add("main");
        stacks.put("main", mainFrames);
        Trace.event("RUNTIME_SCENE",
                "One JVM instance: exactly one heap (shared, empty for now) and one stack for the main thread",
                "Один инстанс JVM: ровно одна куча (общая, пока пустая) и один стек для потока main",
                List.of("heap", "stack:main"), state());
    }

    /**
     * Models a thread starting: a brand-new stack appears for it (with its run method at the
     * bottom). The heap is untouched — there is still only one heap.
     */
    public void startThread(String name) {
        if (stacks.containsKey(name)) {
            return;
        }
        List<String> frames = new ArrayList<>();
        frames.add("run");
        stacks.put(name, frames);
        Trace.event("THREAD_STARTED",
                "Thread " + name + " starts — a new stack appears just for it; now " + stacks.size()
                        + " stacks, still one heap",
                "Поток " + name + " стартует — для него появляется новый стек; теперь стеков " + stacks.size()
                        + ", куча по-прежнему одна",
                List.of("stack:" + name), state());
    }

    /** Models a method call on a given thread: pushes one frame onto that thread's own stack. */
    public void call(String thread, String method) {
        List<String> frames = stacks.get(thread);
        if (frames == null) {
            return;
        }
        frames.add(method);
        int depth = frames.size() - 1;
        Trace.event("STACK_PUSH",
                "Thread " + thread + " calls " + method + "() — a frame is pushed on its own stack only "
                        + "(other threads' stacks are untouched)",
                "Поток " + thread + " вызывает " + method + "() — кадр кладётся только на его стек "
                        + "(стеки других потоков не трогаются)",
                List.of("frame:" + thread + ":" + depth), state());
    }

    /** Models returning from the current method on a thread: pops its top frame. */
    public void ret(String thread) {
        List<String> frames = stacks.get(thread);
        if (frames == null || frames.size() <= 1) {
            return;
        }
        String gone = frames.remove(frames.size() - 1);
        Trace.event("STACK_POP",
                "Thread " + thread + " returns from " + gone + "() — its frame is popped, freeing that "
                        + "thread's stack space",
                "Поток " + thread + " возвращается из " + gone + "() — его кадр снимается, освобождая место "
                        + "в стеке этого потока",
                List.of("stack:" + thread), state());
    }

    /**
     * Models {@code new} executed by a thread. Whatever thread runs it, the object is created in
     * the one shared heap — that is the whole point of the topic.
     */
    public String allocate(String thread, String type) {
        String id = "o" + (++objectSeq);
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("id", id);
        obj.put("type", type);
        obj.put("owner", thread);
        heap.add(obj);
        Trace.event("HEAP_ALLOCATE",
                "Thread " + thread + " runs new " + type + "() — the object lands in the one shared heap, "
                        + "reachable from any thread",
                "Поток " + thread + " выполняет new " + type + "() — объект попадает в единственную общую "
                        + "кучу, доступную любому потоку",
                List.of("obj:" + id, "heap"), state());
        return id;
    }

    /**
     * Models a thread finishing: its stack is discarded (frames and locals gone). Objects it
     * created stay in the shared heap until the garbage collector reclaims the unreachable ones.
     */
    public void endThread(String name) {
        if ("main".equals(name) || !stacks.containsKey(name)) {
            return;
        }
        stacks.remove(name);
        Trace.event("THREAD_EXITED",
                "Thread " + name + " finishes — its whole stack is discarded; now " + stacks.size()
                        + " stacks, but the heap and its objects remain",
                "Поток " + name + " завершается — весь его стек выбрасывается; теперь стеков " + stacks.size()
                        + ", но куча и её объекты остаются",
                List.of("stack:" + name), state());
    }

    /** Number of live stacks right now (one per live thread). */
    public int stackCount() {
        return stacks.size();
    }

    // --- internals -------------------------------------------------------

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("heapCount", 1);
        s.put("stackCount", stacks.size());

        List<Object> stackList = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : stacks.entrySet()) {
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("thread", e.getKey());
            List<Object> frameList = new ArrayList<>();
            for (int i = 0; i < e.getValue().size(); i++) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("depth", i);
                fm.put("name", e.getValue().get(i));
                frameList.add(fm);
            }
            st.put("frames", frameList);
            stackList.add(st);
        }
        s.put("stacks", stackList);

        List<Object> heapList = new ArrayList<>();
        for (Map<String, Object> o : heap) {
            heapList.add(new LinkedHashMap<>(o));
        }
        s.put("heap", heapList);
        return s;
    }
}
