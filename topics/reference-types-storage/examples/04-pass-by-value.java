import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.newObject("order", "Order", "total=100");

        // Calling modify(order): a new frame is pushed and the parameter 'o' gets
        // a COPY of the reference, so it points at the same heap object as 'order'.
        mem.call("modify", "o", "Order", "order");

        // Mutating the shared object IS visible to the caller after return.
        mem.mutateField("o", "total", "90");

        // Reassigning the local parameter is NOT visible to the caller: it only
        // repoints o's own slot inside this frame.
        mem.reassignNew("o", "Order", "total=0");

        // The method returns; its frame and local slots vanish.
        mem.ret();

        System.out.println("Java passes the reference by value: shared object, separate slot.");
    }
}
