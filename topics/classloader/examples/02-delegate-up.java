import visual.VisualClassLoader;

public class Playground {
    public static void main(String[] args) {
        VisualClassLoader app = VisualClassLoader.standardHierarchy();

        // An application class. It still delegates up first: Bootstrap and
        // Platform fail to find it, so the Application loader finally defines it.
        app.loadClass("com.app.Service");
    }
}
