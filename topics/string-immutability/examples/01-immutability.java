import visual.VisualString;

public class Playground {
    public static void main(String[] args) {
        // VisualString is a teaching model of how Strings live in memory.
        VisualString scene = new VisualString();

        // A literal goes into the string pool.
        scene.literal("s", "hello");

        // "s = s + ..." looks like a mutation, but it allocates a NEW String
        // (with a NEW backing array) and re-points s. The original is untouched.
        scene.concat("s", " world");

        System.out.println("Strings are immutable: each change makes a new object.");
    }
}
