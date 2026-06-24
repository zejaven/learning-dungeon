import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.primitive("a", "int", "5");

        // Copying a primitive copies the VALUE into a new, independent slot.
        mem.copyPrimitive("b", "int", "a");

        // Changing the copy does not touch the original: a stays 5.
        mem.setPrimitive("b", "99");

        System.out.println("a is still 5; b is 99 — two separate values.");
    }
}
