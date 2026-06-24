import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        // VisualMemory is a teaching model of what a variable stores and where.
        VisualMemory mem = new VisualMemory();

        // A primitive variable stores its value directly in its stack slot.
        mem.primitive("count", "int", "5");

        // A reference type is allocated on the heap. The variable 'user' holds
        // only a reference (a handle) to that heap object, not the object itself.
        mem.newObject("user", "User", "name=Ann", "age=30");

        System.out.println("Primitives sit in the stack slot; objects live on the heap.");
    }
}
