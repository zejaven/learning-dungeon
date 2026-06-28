import visual.VisualDataTypes;

public class Playground {
    public static void main(String[] args) {
        VisualDataTypes t = new VisualDataTypes();

        // Each primitive has a wrapper reference type: int -> Integer, etc.
        t.primitive("i", "int", "42");

        // Autoboxing wraps the primitive into an Integer OBJECT on the heap.
        t.box("boxed", "Integer", "i");

        // The Integer cache: Integer.valueOf reuses one object for -128..127,
        // so == (identity) is true there but false for larger numbers.
        t.integerCacheCompare(127);   // cached  -> ==  is true
        t.integerCacheCompare(200);   // not cached -> == is false (use equals!)
    }
}
