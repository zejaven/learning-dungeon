import visual.VisualMemoryLeak;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryLeak heap = new VisualMemoryLeak("static-cache");

        // A static collection lives for the whole JVM lifetime.
        heap.longLivedRoot("staticCache");

        // A request builds an Entry and also stores it in the static cache.
        heap.allocate("entry", "Entry", "handleRequest");
        heap.addReference("entry", "staticCache");

        // The request finishes; its local reference is gone.
        heap.exitScope("handleRequest");

        // The cache still holds the Entry, so the GC cannot free it: a leak that
        // grows with every request because nothing ever removes old entries.
        heap.gc();
    }
}
