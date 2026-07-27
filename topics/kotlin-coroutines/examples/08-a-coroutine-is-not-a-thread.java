import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        // The same 100 000 concurrent operations, priced three ways.
        VisualCoroutines.compareScale(100_000);

        System.out.println("A suspended coroutine is one object on the heap. A parked thread is a stack.");
    }
}
