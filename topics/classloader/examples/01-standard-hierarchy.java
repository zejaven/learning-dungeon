import visual.VisualClassLoader;

public class Playground {
    public static void main(String[] args) {
        // VisualClassLoader is a teaching model of the JVM class-loading chain.
        // standardHierarchy() builds Bootstrap -> Platform -> Application.
        VisualClassLoader app = VisualClassLoader.standardHierarchy();

        // A core JDK class. The request starts at the Application loader but is
        // delegated all the way up — Bootstrap owns java.* classes.
        app.loadClass("java.lang.String");
    }
}
