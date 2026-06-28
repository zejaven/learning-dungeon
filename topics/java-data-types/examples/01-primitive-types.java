import visual.VisualDataTypes;

public class Playground {
    public static void main(String[] args) {
        // VisualDataTypes is a teaching model of Java's type families.
        VisualDataTypes t = new VisualDataTypes();

        // The eight primitive types: each has a fixed size and value range,
        // stores its value directly, and is never null.
        t.primitive("b", "byte", "127");        // 8-bit integer
        t.primitive("sh", "short", "30000");    // 16-bit integer
        t.primitive("i", "int", "1000000");     // 32-bit integer (the default int)
        t.primitive("big", "long", "9000000000"); // 64-bit integer
        t.primitive("f", "float", "3.14");      // 32-bit floating point
        t.primitive("d", "double", "3.14159");  // 64-bit floating point (the default real)
        t.primitive("c", "char", "A");          // 16-bit unsigned character code
        t.primitive("flag", "boolean", "true"); // true / false

        // A reference type is the other family: it holds a handle to a heap
        // object and can be null.
        t.reference("name", "String", "\"Anna\"");
    }
}
