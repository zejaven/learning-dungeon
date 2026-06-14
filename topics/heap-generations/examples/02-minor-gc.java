import visual.VisualHeap;
import visual.VisualHeap.Ref;

public class Playground {
    public static void main(String[] args) {
        VisualHeap heap = new VisualHeap("heap");

        Ref a = heap.allocate("a");
        Ref b = heap.allocate("b");
        Ref t1 = heap.allocate("t1");
        Ref t2 = heap.allocate("t2");

        // t1 and t2 are short-lived: drop the references so they become garbage.
        heap.drop(t1);
        heap.drop(t2);

        // Eden is now full (4). Allocating one more triggers a minor GC first:
        // the dead temporaries are freed, a and b survive and are copied into a
        // Survivor space with their age incremented.
        heap.allocate("c");

        System.out.println("a and b survived; t1 and t2 were collected.");
    }
}
