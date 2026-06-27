import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // p references an object on the heap.
        mem.newObject("p", "Point", "x=1", "y=2");

        // Point q = p; copies the REFERENCE, not the object. Now p and q are two
        // stack slots pointing at the SAME heap object (aliasing).
        mem.copyReference("q", "Point", "p");

        // Mutating the shared object through q is visible through p too —
        // because there is only one object on the heap.
        mem.mutateField("q", "x", "99");
    }
}
