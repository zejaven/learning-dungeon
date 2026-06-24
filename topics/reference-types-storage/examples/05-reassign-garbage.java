import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.newObject("cart", "Cart", "items=3");

        // Repointing cart at a brand-new object leaves the first Cart unreachable.
        // It is not deleted now — it waits on the heap for the garbage collector.
        mem.reassignNew("cart", "Cart", "items=0");

        System.out.println("The old Cart stays on the heap until the GC reclaims it.");
    }
}
