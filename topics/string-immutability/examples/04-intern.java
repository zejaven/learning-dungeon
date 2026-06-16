import visual.VisualString;

public class Playground {
    public static void main(String[] args) {
        VisualString scene = new VisualString();

        // A literal lives in the pool.
        scene.literal("a", "spring");

        // A runtime-built string is a separate heap object, even with equal text.
        scene.newString("b", "spring");
        scene.compare("a", "b"); // false: distinct objects

        // intern() returns the canonical pooled instance for that content.
        scene.intern("b");
        scene.compare("a", "b"); // true: now both point at the pooled object

        System.out.println("intern() canonicalizes a string into the pool.");
    }
}
