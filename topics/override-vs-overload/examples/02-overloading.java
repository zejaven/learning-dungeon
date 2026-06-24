import visual.VisualDispatch;

public class Playground {
    public static void main(String[] args) {
        VisualDispatch scene = new VisualDispatch();

        // class Printer { void print(Object o); void print(String s); }
        // Same name, different parameter types -> an overload set.
        scene.declareClass("Printer", null);
        scene.method("Printer", "print(Object)");
        scene.method("Printer", "print(String)");

        // String s = "hi";  -> static type String, runtime type String
        scene.variable("s", "String", "String");

        // p.print(s) is OVERLOADING: chosen at compile time by the static type of s.
        // The most specific applicable overload, print(String), wins.
        scene.overloadCall("Printer", "print", "s");
    }
}
