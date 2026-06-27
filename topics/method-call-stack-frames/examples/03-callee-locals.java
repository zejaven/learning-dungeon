import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.primitive("callerTotal", "int", "100");
        mem.newObject("cart", "Cart", "items=2");

        // applyDiscount() receives its own frame and its own local slots.
        mem.call("applyDiscount", "cartParam", "Cart", "cart");
        mem.primitive("calleeDiscount", "int", "15");
        mem.primitive("calleeTotal", "int", "85");
        mem.setPrimitive("calleeTotal", "80");

        // The callee frame disappears; callerTotal was a different slot.
        mem.ret();
        mem.setPrimitive("callerTotal", "100");
    }
}
