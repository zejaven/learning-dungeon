import visual.VisualStringConcat;

public class Playground {
    public static void main(String[] args) {
        VisualStringConcat scene = new VisualStringConcat();

        // The naive way: result = result + piece, in a loop.
        // Because String is immutable, EACH iteration allocates a brand-new
        // String and copies every character seen so far. The previous String
        // becomes garbage. Watch "chars copied" and "garbage" explode.
        scene.concatLoop("ab", 6);

        // Scale this to 1,000,000 pieces and the copying is O(n^2):
        // millions of dead objects and gigabytes of needless copying.
        System.out.println("Never use + in a loop to build a big string.");
    }
}
