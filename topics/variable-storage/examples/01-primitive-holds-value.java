import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        // VisualMemory is a teaching model of where Java variables live.
        VisualMemory mem = new VisualMemory();

        // A primitive variable stores the value itself, in its own stack slot.
        mem.primitive("a", "int", "5");

        // int b = a; copies the VALUE into a separate slot.
        mem.copyPrimitive("b", "int", "a");

        // Changing b does not affect a — they are independent slots.
        mem.setPrimitive("b", "99");
    }
}
