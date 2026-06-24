import visual.VisualSingleton;

public class Playground {
    public static void main(String[] args) {
        VisualSingleton registry = VisualSingleton.synchronizedLazy("ConfigRegistry");

        registry.synchronizedGet("T1");
        registry.synchronizedGet("T2");
    }
}
