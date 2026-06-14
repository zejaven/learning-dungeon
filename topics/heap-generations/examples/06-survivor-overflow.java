import visual.VisualHeap;

public class Playground {
    public static void main(String[] args) {
        VisualHeap heap = new VisualHeap("heap");

        // Fill Eden with four objects that all stay reachable.
        heap.allocate("a");
        heap.allocate("b");
        heap.allocate("c");
        heap.allocate("d");

        // Allocating a fifth triggers a minor GC, but all four young objects are
        // still live. A Survivor space only holds 3, so the overflow object is
        // promoted to Old early — "premature promotion" — even though it is
        // young and might die soon. This pollutes the Old generation.
        heap.allocate("e");

        System.out.println("Survivor overflowed; one object was promoted prematurely.");
    }
}
