import visual.VisualMemoryLeak;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryLeak heap = new VisualMemoryLeak("bounded-cache");

        heap.longLivedRoot("staticCache");

        heap.allocate("entry", "Entry", "handleRequest");
        heap.addReference("entry", "staticCache");

        heap.exitScope("handleRequest");

        // Evict the entry when it is no longer needed (eviction policy / size cap).
        heap.dropReference("entry", "staticCache");

        // Now no GC root references the Entry, so the GC reclaims it. No leak.
        heap.gc();
    }
}
