import visual.VisualString;

public class Playground {
    public static void main(String[] args) {
        VisualString scene = new VisualString();

        scene.literal("s", "cat");

        // "Changing a character" is impossible in place: replace() returns a NEW
        // String with a NEW backing array. The old 'cat' object survives as
        // garbage until the GC reclaims it.
        scene.replace("s", 'c', 'b'); // -> "bat"

        // toUpperCase() is the same story: another new object.
        scene.toUpperCase("s"); // -> "BAT"

        System.out.println("There is no in-place edit — only new Strings.");
    }
}
