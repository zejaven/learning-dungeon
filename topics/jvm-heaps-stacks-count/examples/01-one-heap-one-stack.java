import visual.VisualRuntimeAreas;

public class Playground {
    public static void main(String[] args) {
        // A single JVM instance always boots with exactly ONE heap (shared) and
        // ONE stack for the main thread. That is the baseline answer.
        VisualRuntimeAreas jvm = new VisualRuntimeAreas();

        // main() does some work: a call pushes a frame on main's own stack.
        jvm.call("main", "run");

        // new puts an object in the one shared heap, even when only one thread exists.
        jvm.allocate("main", "Config");

        // Returning pops the frame; the heap object stays where it is.
        jvm.ret("main");
    }
}
