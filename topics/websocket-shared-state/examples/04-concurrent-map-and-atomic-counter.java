import visual.VisualSharedState;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

public class Playground {
    public static void main(String[] args) {
        // One singleton bean holding a ConcurrentHashMap<String, Session>
        // and an AtomicLong - the single-process answer.
        VisualSharedState server = VisualSharedState.server(
                Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER);

        server.connect("alice");
        server.connect("bob");
        server.connect("carol");

        // Exactly the interleaving that broke the previous examples.
        server.beginJoin("alice");
        server.beginJoin("bob");
        server.finishJoin("alice");
        server.finishJoin("bob");
        server.join("carol");

        server.broadcast("market open");

        server.disconnect("carol");             // @OnClose removes the entry
        server.disconnectWithoutCleanup("bob"); // and here somebody forgot

        server.broadcast("market close");

        server.report();
        System.out.println("Registering is half the job; removing is the other half.");
    }
}
