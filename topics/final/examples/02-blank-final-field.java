import visual.VisualFinal;

public class Playground {
    public static void main(String[] args) {
        VisualFinal f = new VisualFinal();

        // A "blank final" field: declared `final` but without a value, e.g.
        // `final int id;`. It is still unlocked at this point.
        f.blankField("id", "int");

        // The constructor must assign it exactly once. After that the binding
        // is locked.
        f.assignOnce("id", "42");

        // A second assignment (even in the constructor) does not compile.
        f.reassignBlocked("id", "43");
    }
}
