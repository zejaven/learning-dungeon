import visual.VisualSharedState;
import visual.VisualSharedState.Source;
import visual.VisualSharedState.Store;

public class Playground {
    public static void main(String[] args) {
        // The same correct code, deployed twice behind a load balancer.
        VisualSharedState servers = VisualSharedState.cluster(
                Store.CONCURRENT_MAP, Source.ATOMIC_COUNTER, 2);

        servers.connect("alice");  // routed to app-1
        servers.connect("bob");    // routed to app-2

        servers.join("alice");
        servers.join("bob");

        servers.broadcast("market open");

        servers.report();
        System.out.println("Two AtomicLongs are two counters, not one.");
    }
}
