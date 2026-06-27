import visual.VisualRefHeap;

public class Playground {
    public static void main(String[] args) {
        // A strong reference is an ordinary one: as long as it lives, the
        // object is reachable and the GC will never reclaim it.
        VisualRefHeap heap = new VisualRefHeap();

        heap.allocate("A", "Photo");
        heap.strong("s", "A");

        heap.gc();        // strongly reachable -> survives
        heap.drop("s");   // s = null: now nothing strong holds it
        heap.gc();        // unreachable -> collected
    }
}
