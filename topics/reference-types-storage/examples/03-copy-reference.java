import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // One object on the heap.
        mem.newObject("p", "Point", "x=1", "y=2");

        // Copying a reference copies only the handle: p and q point at the SAME
        // object. No new object is created.
        mem.copyReference("q", "Point", "p");

        // Mutating through q is visible through p — there is only one object.
        mem.mutateField("q", "x", "99");

        System.out.println("p and q are two names for one heap object.");
    }
}
