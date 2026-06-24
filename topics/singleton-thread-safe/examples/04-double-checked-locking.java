import visual.VisualSingleton;

public class Playground {
    public static void main(String[] args) {
        VisualSingleton registry = VisualSingleton.doubleCheckedLocking("ConfigRegistry");

        registry.doubleCheckedGet("T1");
        registry.doubleCheckedGet("T2");
    }
}
