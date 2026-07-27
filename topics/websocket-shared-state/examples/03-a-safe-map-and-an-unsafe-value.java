import visual.VisualSharedState;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

public class Playground {
    public static void main(String[] args) {
        // The map is thread-safe. The way the value is computed is not.
        VisualSharedState server = VisualSharedState.server(
                Store.SYNCHRONIZED_MAP, Source.REGISTRY_SIZE);

        server.connect("alice");
        server.connect("bob");
        server.connect("carol");
        server.connect("dan");

        // Two threads read sessions.size() before either of them adds anything.
        server.beginJoin("alice");
        server.beginJoin("bob");
        server.finishJoin("alice");
        server.finishJoin("bob");

        server.join("carol");

        // size() does not only lag behind - it also goes backwards.
        server.disconnect("alice");

        server.beginJoin("dan");
        server.broadcast("market open");
        server.finishJoin("dan");

        server.report();
        System.out.println("Thread-safe collection, unsafe sequence of calls.");
    }
}
