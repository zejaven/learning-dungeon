import visual.VisualMemoryAreas;

public class Playground {
    public static void main(String[] args) {
        // Booting one JVM instance creates the shared runtime data areas, one of
        // each: the heap (with the string pool inside it), Metaspace, code cache.
        VisualMemoryAreas jvm = new VisualMemoryAreas();

        // Loading a class stores its METADATA (fields, methods, constant pool)
        // in Metaspace — native memory, outside the heap and outside -Xmx.
        jvm.loadClass("Order");

        // Running new stores the OBJECT itself in the heap. Two different areas
        // for two different things: the blueprint and the instance.
        jvm.allocate("main", "Order");

        // Count what this single instance actually has right now.
        jvm.countAreas();

        System.out.println("shared areas: " + jvm.sharedAreaCount());
        System.out.println("threads, and therefore stacks: " + jvm.threadCount());
    }
}
