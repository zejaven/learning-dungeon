import visual.VisualMemoryLeak;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryLeak heap = new VisualMemoryLeak("listeners");

        // A long-lived publisher keeps its subscriber list for the whole app.
        heap.longLivedRoot("publisher");

        // A short-lived view subscribes to the publisher.
        heap.allocate("view", "Listener", "openView");
        heap.addReference("view", "publisher");

        // The view is closed, but it forgot to unsubscribe.
        heap.exitScope("openView");
        heap.gc(); // leak: the publisher still references the dead view

        // The fix: unregister the listener so the publisher releases it.
        heap.dropReference("view", "publisher");
        heap.gc(); // now the Listener is collected
    }
}
