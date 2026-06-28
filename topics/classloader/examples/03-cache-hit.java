import visual.VisualClassLoader;

public class Playground {
    public static void main(String[] args) {
        VisualClassLoader app = VisualClassLoader.standardHierarchy();

        // First load: Bootstrap defines java.util.HashMap and caches it.
        app.loadClass("java.util.HashMap");

        // Second load: the same class is already in Bootstrap's cache, so it is
        // returned without reloading. A class is loaded once per loader.
        app.loadClass("java.util.HashMap");
    }
}
