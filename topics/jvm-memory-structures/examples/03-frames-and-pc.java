import visual.VisualMemoryAreas;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryAreas jvm = new VisualMemoryAreas();

        // Each call pushes one frame holding that call's local variables and
        // partial results onto the calling thread's OWN stack.
        jvm.call("main", "checkout");
        jvm.call("main", "total");

        // The reference lives in the frame; the object it points at is in the heap.
        jvm.allocate("main", "Receipt");

        // Returning pops the frame: its locals vanish immediately, with no GC
        // involved. The PC register then points back into the caller.
        jvm.ret("main");
        jvm.ret("main");

        System.out.println("one stack per thread, one frame per active call");
    }
}
