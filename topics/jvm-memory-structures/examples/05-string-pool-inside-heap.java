import visual.VisualMemoryAreas;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryAreas jvm = new VisualMemoryAreas();

        // A string literal is interned: it is stored in the string pool, which
        // since Java 7 is a table INSIDE the heap (before that it was in PermGen).
        jvm.internString("main", "OK");

        // The same literal again reuses the pooled instance — no second object.
        jvm.internString("main", "OK");

        // new String("OK") deliberately skips the pool: a separate object is
        // allocated in the heap, which is why == comparison then fails.
        jvm.allocate("main", "String");

        System.out.println("the pool is in the heap, but not every String is in the pool");
    }
}
