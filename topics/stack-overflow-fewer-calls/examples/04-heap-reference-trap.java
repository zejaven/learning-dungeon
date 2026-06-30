import visual.VisualStackPressure;

public class Playground {
    public static void main(String[] args) {
        // The large array is modeled on the heap. Frames keep only a reference.
        VisualStackPressure stack = new VisualStackPressure(384);
        stack.allocateHeapObject("byte[1_000_000]", 1_000_000);
        stack.recurseUntilOverflow("passesBigArray", 56, "byte[] data ref", "int offset");
    }
}
