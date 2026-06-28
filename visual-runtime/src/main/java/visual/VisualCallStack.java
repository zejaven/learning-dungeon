package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the <strong>thread call stack and StackOverflowError</strong>.
 * It is NOT a real JVM: it reproduces the mental model interviewers ask about — every
 * method call pushes a new <em>stack frame</em>, the stack has a <em>fixed maximum size</em>
 * (set by {@code -Xss}), and a chain of calls that never returns (recursion with no
 * reachable base case) keeps pushing frames until the stack is full. The next push then
 * fails: the JVM throws {@link StackOverflowError}.
 *
 * <p>The model uses a tiny {@code limit} (a handful of frames) so the overflow is visible
 * in a few steps instead of the thousands of frames a real stack holds. It never actually
 * recurses without bound — it simulates the pushes — so an example runs cleanly while still
 * showing exactly what an unbounded recursion does to the stack.
 *
 * <p>It emits {@link Trace} events so the UI can draw frames piling up and the moment the
 * stack overflows. Differences from the real JVM are intentional: the real limit is in
 * bytes (each frame's size depends on its locals), not a fixed frame count, and a real
 * StackOverflowError is a throwable you can (rarely) catch — here the overflow is just the
 * final event.
 */
public class VisualCallStack {

    /** The call stack: index 0 is the bottom-most frame ("main"). */
    private final List<String> frames = new ArrayList<>();
    /** Maximum number of frames this stack can hold before it overflows. */
    private final int limit;
    /** True once a push has exceeded {@link #limit}. */
    private boolean overflowed;

    /** Creates a stack with room for {@code limit} frames (including {@code main}). */
    public VisualCallStack(int limit) {
        this.limit = Math.max(2, limit);
        frames.add("main");
        Trace.event("STACK_SCENE",
                "Fresh thread: one frame main() at the bottom, room for " + this.limit + " frames in total",
                "Свежий поток: один кадр main() внизу, всего помещается " + this.limit + " кадров",
                List.of("frame:0"), state());
    }

    /**
     * Models a method call: pushes one frame. If the stack was already full this push
     * overflows and the JVM would throw {@link StackOverflowError}. Returns {@code true}
     * once the stack has overflowed, so an example can drive an "infinite" recursion with
     * {@code while (!stack.call("recurse")) {}} without actually recursing forever.
     */
    public boolean call(String method) {
        if (overflowed) {
            return true;
        }
        if (frames.size() >= limit) {
            overflowed = true;
            Trace.event("STACK_OVERFLOW",
                    "No room for another " + method + "() frame — the stack is full, so the JVM throws "
                            + "StackOverflowError",
                    "Места для ещё одного кадра " + method + "() нет — стек переполнен, поэтому JVM бросает "
                            + "StackOverflowError",
                    List.of("frame:" + (frames.size() - 1), "overflow"), state());
            return true;
        }
        frames.add(method);
        int depth = frames.size() - 1;
        Trace.event("STACK_PUSH",
                "Call " + method + "() — a new frame is pushed at depth " + depth + " (" + frames.size()
                        + "/" + limit + " frames used)",
                "Вызов " + method + "() — новый кадр кладётся на глубину " + depth + " (" + frames.size()
                        + "/" + limit + " кадров занято)",
                List.of("frame:" + depth), state());
        return false;
    }

    /**
     * Models returning from the current method: the top frame is popped and its locals
     * vanish. A correct recursion reaches its base case and pops every frame, so it never
     * fills the stack.
     */
    public void ret() {
        if (frames.size() > 1 && !overflowed) {
            String gone = frames.remove(frames.size() - 1);
            Trace.event("STACK_POP",
                    gone + "() returns — its frame is popped, freeing stack space (" + frames.size()
                            + "/" + limit + " frames used)",
                    gone + "() возвращается — его кадр снимается, освобождая место в стеке (" + frames.size()
                            + "/" + limit + " кадров занято)",
                    List.of("frame:" + (frames.size() - 1)), state());
        }
    }

    /** Convenience for the "infinite recursion" examples: keep calling until the stack overflows. */
    public void recurseUntilOverflow(String method) {
        while (!call(method)) {
            // each iteration models one more self-call that never returns
        }
    }

    /** True once the stack has overflowed. */
    public boolean isOverflowed() {
        return overflowed;
    }

    // --- internals -------------------------------------------------------

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("limit", limit);
        s.put("overflowed", overflowed);
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("depth", i);
            fm.put("name", frames.get(i));
            list.add(fm);
        }
        s.put("frames", list);
        return s;
    }
}
