import visual.VisualString;

public class Playground {
    public static void main(String[] args) {
        VisualString scene = new VisualString();

        // A StringBuilder is mutable: it has one object you keep editing.
        scene.builder("sb", "a");

        // Each append() changes the SAME object in place — no new allocation.
        scene.append("sb", "b");
        scene.append("sb", "c");
        scene.append("sb", "d");

        // Compare with example 1: String + String allocated a new object each time.
        System.out.println("Build long strings with StringBuilder, not + in a loop.");
    }
}
