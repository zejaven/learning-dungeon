import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // p references an object on the heap.
        mem.newObject("p", "Point", "x=1", "y=2");

        // p = null; the slot still exists, but now references no object at all.
        // The object p used to point at can be garbage collected.
        mem.setNull("p");
    }
}
