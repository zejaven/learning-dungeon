import visual.VisualRefHeap;

public class Playground {
    public static void main(String[] args) {
        // Reachability is decided by the STRONGEST live reference. An object
        // referenced both strongly and weakly is strongly reachable: the weak
        // reference is irrelevant until the strong one is gone.
        VisualRefHeap heap = new VisualRefHeap();

        heap.allocate("A", "Config");
        heap.strong("s", "A");
        heap.weak("w", "A");

        heap.gc();        // strong wins -> survives, weak NOT cleared

        heap.drop("s");   // now only the weak reference remains
        heap.gc();        // weak-only -> collected
    }
}
