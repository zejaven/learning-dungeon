import visual.VisualRefHeap;

public class Playground {
    public static void main(String[] args) {
        // A phantom reference is a post-mortem hook: its get() is ALWAYS null,
        // and it is enqueued only AFTER the object has been collected. You poll
        // the ReferenceQueue to run cleanup (e.g. free a native buffer) safely,
        // without the unpredictability of finalize().
        VisualRefHeap heap = new VisualRefHeap();

        heap.allocate("R", "NativeBuffer");
        heap.strong("s", "R");
        heap.phantom("ph", "R"); // registered with a ReferenceQueue

        System.out.println("phantom.get() = " + heap.get("ph")); // always null

        heap.drop("s");   // release the strong reference
        heap.gc();        // collected -> phantom enqueued for cleanup
    }
}
