import visual.VisualClassLoader;

public class Playground {
    public static void main(String[] args) {
        VisualClassLoader app = VisualClassLoader.standardHierarchy();

        // No loader in the chain knows this class. The request bubbles all the
        // way up to Bootstrap, comes back empty, and ends in ClassNotFoundException.
        app.loadClass("com.unknown.Missing");
    }
}
