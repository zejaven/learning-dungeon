import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // p points at a fresh Point on the heap.
        mem.newObject("p", "Point", "x=1", "y=2");

        // Point q = p; copies the REFERENCE, not the object.
        // Now q and p are two names for the same heap object.
        mem.copyReference("q", "Point", "p");

        // q.x = 99; mutates that one shared object — visible through p too.
        mem.mutateField("q", "x", "99");
    }
}
