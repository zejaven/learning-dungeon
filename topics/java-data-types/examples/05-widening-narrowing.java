import visual.VisualDataTypes;

public class Playground {
    public static void main(String[] args) {
        VisualDataTypes t = new VisualDataTypes();

        // Start with a long that holds 300.
        t.primitive("big", "long", "300");

        // Widening (long fits into nothing smaller, so use int -> long below):
        t.primitive("i", "int", "100");
        // int -> long is widening: implicit and lossless.
        t.widen("wide", "long", "i");

        // Narrowing long -> byte needs an explicit cast and can lose data:
        // a byte only holds -128..127, so (byte) 300 silently becomes 44.
        t.narrow("small", "byte", "big");
    }
}
