import visual.VisualDataTypes;

public class Playground {
    public static void main(String[] args) {
        VisualDataTypes t = new VisualDataTypes();

        // An int is a fixed 32-bit box. Its maximum is 2147483647.
        t.primitive("max", "int", "2147483647");

        // Adding 1 does not make it bigger — there is no room above MAX_VALUE,
        // so it wraps around to the minimum, -2147483648.
        t.overflowInt("max");
    }
}
