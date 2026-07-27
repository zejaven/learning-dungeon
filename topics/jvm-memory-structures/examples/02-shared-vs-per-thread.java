import visual.VisualMemoryAreas;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryAreas jvm = new VisualMemoryAreas();
        jvm.allocate("main", "Cache");

        // Every thread that starts brings THREE private areas with it:
        // a JVM stack, a PC register and a native method stack.
        jvm.startThread("worker-1");
        jvm.startThread("worker-2");
        jvm.countAreas();

        // But new does NOT create a new heap: worker-1 allocates into the same
        // single heap that main uses. That is why the object can be shared.
        jvm.allocate("worker-1", "Task");

        // When a thread finishes, its three private areas are discarded — the
        // shared areas and the objects it created stay behind.
        jvm.endThread("worker-1");
        jvm.countAreas();

        System.out.println("live threads: " + jvm.threadCount());
        System.out.println("heaps: 1, always");
    }
}
