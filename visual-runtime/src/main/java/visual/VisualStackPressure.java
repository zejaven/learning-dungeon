package visual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A teaching model of stack byte pressure: the thread stack has a fixed byte
 * budget, and every active method call consumes one frame from that budget.
 *
 * <p>This is intentionally small and deterministic. It does not inspect real JVM
 * frames; examples choose illustrative frame sizes so learners can see why a
 * larger frame or a smaller {@code -Xss} reaches StackOverflowError after fewer
 * recursive calls.
 */
public class VisualStackPressure {

    private static final int MAIN_FRAME_BYTES = 64;

    private final int stackBytes;
    private final List<Frame> frames = new ArrayList<>();
    private final List<HeapObject> heapObjects = new ArrayList<>();
    private boolean overflowed;
    private int nextHeapId = 1;

    public VisualStackPressure(int stackBytes) {
        this.stackBytes = Math.max(128, stackBytes);
        frames.add(new Frame("main", MAIN_FRAME_BYTES, List.of("String[] args ref")));
        Trace.event("STACK_BUDGET_SCENE",
                "Thread stack budget is " + this.stackBytes + " bytes in this model; main() already uses "
                        + MAIN_FRAME_BYTES + " bytes",
                "В этой модели бюджет стека потока — " + this.stackBytes + " байт; main() уже использует "
                        + MAIN_FRAME_BYTES + " байта",
                List.of("frame:0", "budget"), state(null));
    }

    /**
     * Models a method call. Returns {@code true} when the call would overflow the
     * stack, so examples can drive recursion without crashing the real JVM.
     */
    public boolean call(String method, int frameBytes, String... locals) {
        if (overflowed) {
            return true;
        }

        int bytes = Math.max(16, frameBytes);
        List<String> localList = Arrays.asList(locals);
        int remaining = stackBytes - usedBytes();
        Map<String, Object> attempt = attempt(method, bytes, localList, bytes <= remaining);

        if (bytes > remaining) {
            overflowed = true;
            Trace.event("STACK_FRAME_OVERFLOW",
                    "Call " + method + "() needs a " + bytes + "-byte frame, but only " + remaining
                            + " bytes remain — StackOverflowError happens after fewer calls",
                    "Вызову " + method + "() нужен кадр " + bytes + " байт, но осталось только " + remaining
                            + " байт — StackOverflowError случается после меньшего числа вызовов",
                    List.of("budget", "overflow"), state(attempt));
            return true;
        }

        frames.add(new Frame(method, bytes, localList));
        int depth = frames.size() - 1;
        Trace.event("STACK_FRAME_PUSH",
                "Call " + method + "() uses a " + bytes + "-byte frame; stack usage is now "
                        + usedBytes() + "/" + stackBytes + " bytes",
                "Вызов " + method + "() использует кадр " + bytes + " байт; теперь занято "
                        + usedBytes() + "/" + stackBytes + " байт стека",
                List.of("frame:" + depth, "budget"), state(null));
        return false;
    }

    /**
     * Models returning from the current method: the top frame disappears and its
     * stack bytes become available again.
     */
    public void ret() {
        if (overflowed || frames.size() <= 1) {
            return;
        }
        Frame gone = frames.remove(frames.size() - 1);
        Trace.event("STACK_FRAME_POP",
                gone.name + "() returns; its " + gone.bytes + "-byte frame is removed and stack space is reused",
                gone.name + "() возвращается; его кадр " + gone.bytes
                        + " байт удаляется, и место в стеке используется снова",
                List.of("frame:" + (frames.size() - 1), "budget"), state(null));
    }

    /**
     * Adds a heap object to the picture. The current stack frame stores only a
     * reference; the object bytes are not copied into every recursive frame.
     */
    public void allocateHeapObject(String label, int bytes) {
        HeapObject object = new HeapObject(nextHeapId++, label, Math.max(0, bytes));
        heapObjects.add(object);
        Trace.event("HEAP_OBJECT_ALLOCATED",
                label + " is modeled on the heap; recursive frames keep only references to it",
                label + " показан в heap; рекурсивные кадры хранят только ссылки на него",
                List.of("heap:" + object.id), state(null));
    }

    /** Keeps pushing the same method frame until the simulated stack overflows. */
    public void recurseUntilOverflow(String method, int frameBytes, String... locals) {
        while (!call(method, frameBytes, locals)) {
            // each loop iteration models one more recursive call that has not returned
        }
    }

    public boolean isOverflowed() {
        return overflowed;
    }

    private int usedBytes() {
        int used = 0;
        for (Frame frame : frames) {
            used += frame.bytes;
        }
        return used;
    }

    private Object state(Map<String, Object> attempt) {
        Map<String, Object> s = new LinkedHashMap<>();
        int used = usedBytes();
        s.put("stackBytes", stackBytes);
        s.put("usedBytes", used);
        s.put("remainingBytes", Math.max(0, stackBytes - used));
        s.put("overflowed", overflowed);

        List<Object> frameList = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("depth", i);
            fm.put("name", frame.name);
            fm.put("bytes", frame.bytes);
            fm.put("locals", frame.locals);
            frameList.add(fm);
        }
        s.put("frames", frameList);

        List<Object> heapList = new ArrayList<>();
        for (HeapObject object : heapObjects) {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("id", object.id);
            hm.put("label", object.label);
            hm.put("bytes", object.bytes);
            heapList.add(hm);
        }
        s.put("heapObjects", heapList);
        s.put("attempt", attempt);
        return s;
    }

    private Map<String, Object> attempt(String method, int bytes, List<String> locals, boolean fits) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", method);
        a.put("bytes", bytes);
        a.put("locals", locals);
        a.put("fits", fits);
        return a;
    }

    private static final class Frame {
        private final String name;
        private final int bytes;
        private final List<String> locals;

        private Frame(String name, int bytes, List<String> locals) {
            this.name = name;
            this.bytes = bytes;
            this.locals = new ArrayList<>(locals);
        }
    }

    private static final class HeapObject {
        private final int id;
        private final String label;
        private final int bytes;

        private HeapObject(int id, String label, int bytes) {
            this.id = id;
            this.label = label;
            this.bytes = bytes;
        }
    }
}
