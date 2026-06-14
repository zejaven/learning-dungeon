import visual.VisualHeap;

public class Playground {
    public static void main(String[] args) {
        // VisualHeap is a teaching model of a generational JVM heap.
        VisualHeap heap = new VisualHeap("heap");

        // New objects are born in Eden with a fast bump-pointer allocation.
        // While Eden has room, no GC runs at all.
        heap.allocate("user");
        heap.allocate("order");
        heap.allocate("cart");

        System.out.println("Three young objects live in Eden.");
    }
}
