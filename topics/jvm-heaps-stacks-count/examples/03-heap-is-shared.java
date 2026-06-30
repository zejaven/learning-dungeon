import visual.VisualRuntimeAreas;

public class Playground {
    public static void main(String[] args) {
        VisualRuntimeAreas jvm = new VisualRuntimeAreas();
        jvm.startThread("worker-1");

        // No matter which thread runs new, the object is created in the SAME one heap.
        // The "owner" only records who allocated it — the object is reachable from any thread.
        jvm.allocate("main", "Config");
        jvm.allocate("worker-1", "Task");

        // Two threads, two objects, but still exactly one heap holding both of them.
        jvm.call("worker-1", "process");
        jvm.allocate("worker-1", "Result");
    }
}
