import visual.VisualFinal;

public class Playground {
    public static void main(String[] args) {
        VisualFinal f = new VisualFinal();

        // A final method parameter, e.g. `void f(final int n)`. The method
        // receives the argument value and may not reassign the parameter.
        f.parameter("n", "int", "7");

        // Reassigning the parameter inside the body does not compile.
        f.reassignBlocked("n", "8");
    }
}
