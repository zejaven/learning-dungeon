import visual.VisualStatic;

public class Playground {
    public static void main(String[] args) {
        VisualStatic settings = new VisualStatic("Settings");

        // First active use: Java initializes the class once.
        settings.initialize("Settings.mode");

        // Later active use: no second static initialization.
        settings.initialize("Settings.mode");
    }
}
