import visual.VisualDispatch;

public class Playground {
    public static void main(String[] args) {
        // VisualDispatch models how Java picks WHICH method runs.
        VisualDispatch scene = new VisualDispatch();

        // class Animal { String speak() {...} }
        // class Dog extends Animal { @Override String speak() {...} }
        scene.declareClass("Animal", null);
        scene.declareClass("Dog", "Animal");
        scene.method("Animal", "speak()");
        scene.method("Dog", "speak()"); // overrides Animal.speak()

        // Animal a = new Dog();  -> static type Animal, runtime type Dog
        scene.variable("a", "Animal", "Dog");

        // a.speak() is OVERRIDING: chosen at runtime by the actual object (Dog).
        scene.overrideCall("a", "speak");
    }
}
