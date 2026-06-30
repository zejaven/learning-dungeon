import visual.VisualStackPressure;

public class Playground {
    public static void main(String[] args) {
        // A smaller stack budget acts like running the JVM with a smaller -Xss.
        VisualStackPressure stack = new VisualStackPressure(224);
        stack.recurseUntilOverflow("regularStep", 64, "int n", "int result");
    }
}
