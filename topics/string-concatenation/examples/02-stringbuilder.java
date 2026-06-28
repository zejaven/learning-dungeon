import visual.VisualStringConcat;

public class Playground {
    public static void main(String[] args) {
        VisualStringConcat scene = new VisualStringConcat();

        // The right way: one mutable StringBuilder buffer, reused for every
        // append(). Only the NEW piece is copied each time, into the SAME
        // char[] object — no new String per iteration, almost no garbage.
        scene.builderLoop("ab", 6);

        // For 1,000,000 pieces this stays linear, O(n).
        System.out.println("StringBuilder reuses one buffer instead of reallocating.");
    }
}
