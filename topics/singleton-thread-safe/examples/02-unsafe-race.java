import visual.VisualSingleton;

public class Playground {
    public static void main(String[] args) {
        VisualSingleton registry = VisualSingleton.unsafeLazy("ConfigRegistry");

        registry.unsafeRace("T1", "T2");
    }
}
