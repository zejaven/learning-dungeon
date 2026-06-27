import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // The first object is reachable through p.
        mem.newObject("p", "Point", "x=1", "y=2");

        // p = new Point(7, 8); repoints p at a brand-new object. The first object
        // is now unreachable from any stack slot: it is garbage on the heap.
        mem.reassignNew("p", "Point", "x=7", "y=8");

        // p = null; now p references nothing, so the second object is garbage too.
        // Stack slots are freed automatically; these heap objects wait for the GC.
        mem.setNull("p");
    }
}
