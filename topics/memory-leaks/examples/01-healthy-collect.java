import visual.VisualMemoryLeak;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryLeak heap = new VisualMemoryLeak("healthy");

        // A method allocates a temporary object and uses it locally.
        heap.allocate("temp", "Report", "buildReport");

        // The method returns: its stack frame and local references disappear.
        heap.exitScope("buildReport");

        // Nothing references the object now, so the GC reclaims it. No leak.
        heap.gc();
    }
}
