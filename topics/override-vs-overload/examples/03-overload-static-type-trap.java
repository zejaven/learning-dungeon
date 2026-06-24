import visual.VisualDispatch;

public class Playground {
    public static void main(String[] args) {
        VisualDispatch scene = new VisualDispatch();

        // The classic interview trap.
        scene.declareClass("Printer", null);
        scene.method("Printer", "print(Object)");
        scene.method("Printer", "print(String)");

        // Object o = "hi";  -> static type Object, but the runtime object is a String
        scene.variable("o", "Object", "String");

        // p.print(o) is resolved by the STATIC type (Object), so print(Object) runs.
        // Overloading never looks at the runtime type -> NOT polymorphic.
        scene.overloadCall("Printer", "print", "o");
    }
}
