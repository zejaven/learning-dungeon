import visual.VisualHeap;

public class Playground {
    public static void main(String[] args) {
        VisualHeap heap = new VisualHeap("heap");

        heap.allocate("a");
        heap.allocate("b");

        // First minor GC: live young objects are copied from Eden into S0.
        heap.gc();

        // Second minor GC: the live objects are evacuated from S0 into the empty
        // S1 and aged again. One Survivor is always empty so the other can be
        // copied into it — that is the whole reason there are two.
        heap.gc();

        System.out.println("a and b bounced from S0 to S1, aging each time.");
    }
}
