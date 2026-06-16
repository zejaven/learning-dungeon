import visual.VisualString;

public class Playground {
    public static void main(String[] args) {
        VisualString scene = new VisualString();

        // Two equal literals resolve to the SAME pooled object.
        scene.literal("a", "java");
        scene.literal("b", "java");
        scene.compare("a", "b"); // a == b is true

        // new String(...) forces a separate heap object with the same content.
        scene.newString("c", "java");
        scene.compare("a", "c"); // a == c is false, but a.equals(c) is true

        System.out.println("== compares references; equals() compares content.");
    }
}
