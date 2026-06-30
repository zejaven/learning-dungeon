import visual.VisualStackPressure;

public class Playground {
    public static void main(String[] args) {
        // More primitive locals make each frame larger, so fewer calls fit.
        VisualStackPressure stack = new VisualStackPressure(384);
        stack.recurseUntilOverflow("wideStep", 144,
                "long subtotal", "long tax", "long discount", "long checksum");
    }
}
