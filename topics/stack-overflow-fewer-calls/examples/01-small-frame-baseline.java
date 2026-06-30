import visual.VisualStackPressure;

public class Playground {
    public static void main(String[] args) {
        // A compact recursive call: each active call adds a small frame.
        VisualStackPressure stack = new VisualStackPressure(384);
        stack.recurseUntilOverflow("smallStep", 48, "int n", "ret");
    }
}
