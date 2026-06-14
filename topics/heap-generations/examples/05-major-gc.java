import visual.VisualHeap;
import visual.VisualHeap.Ref;

public class Playground {
    public static void main(String[] args) {
        VisualHeap heap = new VisualHeap("heap");

        Ref cache = heap.allocate("cache");

        // Age 'cache' until it is promoted into the Old generation.
        heap.gc();
        heap.gc();
        heap.gc(); // promoted to Old

        // Now drop it. A minor GC would NEVER reclaim an Old-gen object...
        heap.drop(cache);
        heap.gc(); // minor GC: Old is untouched, 'cache' is still there

        // ...only a major (full) GC scans the Old generation and frees it.
        heap.fullGc();

        System.out.println("Only the major GC could reclaim the Old-gen object.");
    }
}
