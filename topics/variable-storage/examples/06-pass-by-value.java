import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // The caller's variable p references a Point.
        mem.newObject("p", "Point", "x=1", "y=2");

        // modify(p): a new frame is pushed. The parameter gets a COPY of the
        // reference, so it points at the same object as p (pass-by-value).
        mem.call("modify", "param", "Point", "p");

        // Mutating the shared object IS visible to the caller.
        mem.mutateField("param", "x", "99");

        // Reassigning the parameter is NOT visible to the caller: only this
        // frame's slot is repointed; p still points at the original object.
        mem.reassignNew("param", "Point", "x=0", "y=0");

        // The method returns; its frame and local slots vanish.
        mem.ret();
    }
}
