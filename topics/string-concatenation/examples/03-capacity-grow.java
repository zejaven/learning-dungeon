import visual.VisualStringConcat;

public class Playground {
    public static void main(String[] args) {
        VisualStringConcat scene = new VisualStringConcat();

        // A StringBuilder starts with a default capacity of 16. When the buffer
        // fills up it grows ((capacity << 1) + 2 in the JDK), allocating a bigger
        // char[] and copying the existing chars once. Adding 8 pieces of 5 chars
        // (40 chars) overflows 16, so you can watch the buffer grow.
        scene.builderLoop("hello", 8);

        // Growth happens only O(log n) times, so the total copying stays O(n).
        System.out.println("The buffer doubles occasionally, not on every append.");
    }
}
