import visual.VisualSharedState;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

public class Playground {
    public static void main(String[] args) {
        // static Map<String, Session> sessions = new HashMap<>();
        // static long nextId;
        VisualSharedState server = VisualSharedState.server(
                Store.PLAIN_MAP, Source.PLAIN_COUNTER);

        server.connect("alice");
        server.connect("bob");

        // Two @OnOpen callbacks on two threads, interleaved the way a scheduler
        // interleaves them: both read nextId before either writes it back.
        server.beginJoin("alice");
        server.beginJoin("bob");
        server.finishJoin("alice");
        server.finishJoin("bob");

        server.broadcast("market open");

        server.report();
        System.out.println("No exception was thrown anywhere in this run.");
    }
}
