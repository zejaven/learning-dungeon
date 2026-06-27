import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        // VisualMemory is a teaching model of where Java memory lives:
        // a stack of method frames and a heap of objects.
        VisualMemory mem = new VisualMemory();

        // A primitive local lives directly in main()'s stack frame.
        // The slot holds the value 5 itself, not a reference.
        mem.primitive("a", "int", "5");

        // int b = a; copies the VALUE into another slot in the same frame.
        mem.copyPrimitive("b", "int", "a");

        // Changing b touches only b's slot — the stack holds independent values.
        mem.setPrimitive("b", "99");
    }
}
