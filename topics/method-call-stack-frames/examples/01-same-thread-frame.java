import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        // main() has its own local variable and an object reference.
        mem.primitive("requestId", "int", "42");
        mem.newObject("order", "Order", "id=42", "status=draft");

        // validateOrder(order): same thread stack, but a new frame is pushed.
        mem.call("validateOrder", "orderParam", "Order", "order");
        mem.primitive("localChecks", "int", "3");

        // Returning removes only validateOrder()'s frame.
        mem.ret();
    }
}
