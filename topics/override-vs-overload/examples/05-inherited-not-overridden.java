import visual.VisualDispatch;

public class Playground {
    public static void main(String[] args) {
        VisualDispatch scene = new VisualDispatch();

        // Fish does NOT override speak(); it only inherits it.
        scene.declareClass("Animal", null);
        scene.declareClass("Fish", "Animal");
        scene.method("Animal", "speak()"); // Fish has no speak() of its own

        // Animal a = new Fish();
        scene.variable("a", "Animal", "Fish");

        // Dispatch walks up from Fish to the first class that declares speak(),
        // so the inherited Animal.speak() runs.
        scene.overrideCall("a", "speak");
    }
}
