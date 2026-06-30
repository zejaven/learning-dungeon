import visual.VisualStackPressure;

public class Playground {
    public static void main(String[] args) {
        // Iteration lets each helper return before the next item is processed.
        VisualStackPressure stack = new VisualStackPressure(192);
        for (int item = 0; item < 5; item++) {
            if (!stack.call("processOneItem", 64, "int item", "int total")) {
                stack.ret();
            }
        }
    }
}
