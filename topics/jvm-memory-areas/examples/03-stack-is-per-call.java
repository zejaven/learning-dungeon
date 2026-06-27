import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // main() creates an object on the heap.
        mem.newObject("p", "Point", "x=1", "y=2");

        // Calling a method pushes a NEW frame on the same thread's stack.
        // The parameter gets a copy of the reference (pass-by-value).
        mem.call("describe", "param", "Point", "p");

        // A local declared inside describe() lives only in describe()'s frame.
        mem.primitive("len", "int", "2");

        // Returning POPS the frame: param and len vanish with it. The heap object
        // survives because main() still references it through p.
        mem.ret();
    }
}
