import visual.VisualRefHeap;

public class Playground {
    public static void main(String[] args) {
        // A weak reference never keeps an object alive on its own. As soon as
        // no strong/soft reference remains, the next GC clears it. This is how
        // WeakHashMap keys disappear automatically.
        VisualRefHeap heap = new VisualRefHeap();

        heap.allocate("A", "Session");
        heap.strong("s", "A");
        heap.weak("w", "A");

        System.out.println("weak.get() = " + heap.get("w")); // still reachable

        heap.drop("s");   // drop the only strong reference
        heap.gc();        // weak-only -> collected, weak ref cleared

        System.out.println("weak.get() = " + heap.get("w")); // now null
    }
}
