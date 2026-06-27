import visual.VisualRefHeap;

public class Playground {
    public static void main(String[] args) {
        // A soft reference is the right tool for a memory-sensitive cache:
        // the JVM keeps the cached value as long as there is free heap, and
        // clears it ONLY when memory runs low — so the cache shrinks itself.
        VisualRefHeap heap = new VisualRefHeap();

        heap.allocate("img", "BigImage");
        heap.soft("cache", "img"); // cached behind a soft reference

        heap.gc();                                       // plenty of memory
        System.out.println("cache.get() = " + heap.get("cache")); // hit

        heap.gcUnderMemoryPressure();                    // heap is running low
        System.out.println("cache.get() = " + heap.get("cache")); // miss -> recompute
    }
}
