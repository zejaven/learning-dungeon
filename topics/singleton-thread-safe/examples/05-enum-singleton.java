import visual.VisualSingleton;

public class Playground {
    public static void main(String[] args) {
        VisualSingleton registry = VisualSingleton.enumSingleton("ConfigRegistry");

        registry.enumAccess("T1");
        registry.enumAccess("T2");
    }
}
