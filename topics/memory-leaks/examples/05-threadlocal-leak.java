import visual.VisualMemoryLeak;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryLeak heap = new VisualMemoryLeak("thread-local");

        // A pooled worker thread is reused across many tasks; it lives a long time.
        heap.longLivedRoot("poolThread");

        // A task stores a big value in a ThreadLocal bound to that thread.
        heap.allocate("ctx", "UserContext", "task");
        heap.addReference("ctx", "poolThread");

        // The task ends, but it never called ThreadLocal.remove().
        heap.exitScope("task");
        heap.gc(); // leak: the pooled thread still pins the UserContext

        // The fix: ThreadLocal.remove() in a finally block detaches the value.
        heap.dropReference("ctx", "poolThread");
        heap.gc();
    }
}
