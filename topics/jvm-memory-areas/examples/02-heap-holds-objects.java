import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // A primitive value sits in the stack frame.
        mem.primitive("a", "int", "5");

        // new Point(...) allocates the object on the HEAP. The variable p lives
        // on the stack but only holds a reference (a handle) to that heap object.
        // Two different memory areas: the handle on the stack, the object on the heap.
        mem.newObject("p", "Point", "x=1", "y=2");
    }
}
