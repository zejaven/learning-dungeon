import visual.VisualSharedState;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

public class Playground {
    public static void main(String[] args) {
        // The registry is a ConcurrentHashMap and the counter is an AtomicLong,
        // so the shared state is beyond reproach.
        VisualSharedState plain = VisualSharedState.server(
                Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER);
        plain.connect("alice");
        plain.join("alice");

        // A scheduled price push and a reply to an incoming message, at once.
        plain.sendFromTwoThreads("alice", "price 42", "price 43");
        plain.report();

        // ConcurrentWebSocketSessionDecorator, or a lock per session.
        VisualSharedState guarded = VisualSharedState
                .server(Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER)
                .serializeWrites();
        guarded.connect("alice");
        guarded.join("alice");
        guarded.sendFromTwoThreads("alice", "price 42", "price 43");
        guarded.report();

        System.out.println("A concurrent registry says nothing about one session.");
    }
}
