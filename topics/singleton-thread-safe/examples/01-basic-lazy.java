import visual.VisualSingleton;

public class Playground {
    public static void main(String[] args) {
        VisualSingleton registry = VisualSingleton.unsafeLazy("ConfigRegistry");

        registry.unsafeGet("main");
        registry.unsafeGet("request-thread");
    }
}
