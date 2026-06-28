import visual.VisualClassLoader;

public class Playground {
    public static void main(String[] args) {
        VisualClassLoader app = VisualClassLoader.standardHierarchy();

        // A custom loader sits at the bottom of the chain. It still delegates up
        // first; only when no ancestor can find the plugin class does it define
        // the class itself. This is how app servers and plugin systems isolate code.
        VisualClassLoader plugin = app.withChild("PluginLoader", "com.plugin.Greeter");

        plugin.loadClass("com.plugin.Greeter");
    }
}
