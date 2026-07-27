import visual.VisualMemoryAreas;

public class Playground {
    public static void main(String[] args) {
        VisualMemoryAreas jvm = new VisualMemoryAreas();

        // Class metadata is stored ONCE per loaded class, in Metaspace.
        jvm.loadClass("Order");
        jvm.loadClass("OrderLine");

        // Creating two instances adds two objects to the heap, but does NOT add
        // a second copy of Order's metadata: both instances share the one copy.
        jvm.allocate("main", "Order");
        jvm.allocate("main", "Order");

        // A method that runs often enough gets compiled to native code, which is
        // kept in the code cache — a third area, neither heap nor Metaspace.
        jvm.jitCompile("total");

        System.out.println("metadata in Metaspace, objects in the heap, machine code in the code cache");
    }
}
