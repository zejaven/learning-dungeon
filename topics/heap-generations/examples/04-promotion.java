import visual.VisualHeap;
import visual.VisualHeap.Ref;

public class Playground {
    public static void main(String[] args) {
        VisualHeap heap = new VisualHeap("heap");

        Ref session = heap.allocate("session"); // long-lived object
        Ref tmp = heap.allocate("tmp");
        heap.drop(tmp);

        // Each minor GC ages the surviving 'session' by one.
        heap.gc(); // age 1 -> Survivor
        heap.gc(); // age 2 -> Survivor
        heap.gc(); // age 3 reaches the tenuring threshold -> promoted to Old

        System.out.println("session outlived the young gen and was tenured to Old.");
    }
}
