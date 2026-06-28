import visual.VisualFinal;

public class Playground {
    public static void main(String[] args) {
        // VisualFinal is a teaching model of the `final` keyword.
        VisualFinal f = new VisualFinal();

        // A final local variable: assigned once at declaration. The binding
        // (the link from the name `x` to its value) is locked immediately.
        f.localVar("x", "int", "10");

        // Trying to reassign it does not compile: a final variable may be
        // assigned only once. The model rejects the attempt and the binding
        // stays at 10.
        f.reassignBlocked("x", "20");
    }
}
