import visual.VisualDispatch;

public class Playground {
    public static void main(String[] args) {
        VisualDispatch scene = new VisualDispatch();

        // One base, three subclasses; each overrides speak().
        scene.declareClass("Animal", null);
        scene.declareClass("Dog", "Animal");
        scene.declareClass("Cat", "Animal");
        scene.method("Animal", "speak()");
        scene.method("Dog", "speak()");
        scene.method("Cat", "speak()");

        // Same static type Animal, different runtime types.
        scene.variable("d", "Animal", "Dog");
        scene.variable("c", "Animal", "Cat");

        // Each call dispatches to the actual object's body — runtime polymorphism.
        scene.overrideCall("d", "speak");
        scene.overrideCall("c", "speak");
    }
}
