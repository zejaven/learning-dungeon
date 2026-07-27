import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        // Three replicas, each keeping its clients' sessions in its own heap.
        VisualLoadBalancer roundRobin = VisualLoadBalancer.behindBalancer("GET /cart", 3, 4, 2, 6);
        roundRobin.sessionsInMemory();

        // Two clients over three replicas: the rotation cannot keep sending them
        // back to where their state is.
        roundRobin.round("alice", "bob");
        roundRobin.round("alice", "bob");
        roundRobin.round("alice", "bob");
        roundRobin.report();

        // Sticky sessions: pin each client to the replica that holds its state.
        VisualLoadBalancer sticky = VisualLoadBalancer.behindBalancer("GET /cart", 3, 4, 2, 6);
        sticky.sessionsInMemory();
        sticky.strategy(VisualLoadBalancer.Strategy.STICKY);
        sticky.round("alice", "bob");
        sticky.round("alice", "bob");

        // The misses are gone. The state is still in exactly one heap.
        sticky.failNode("app-1");
        sticky.report();

        System.out.println("Statelessness is not a style. It is the precondition for replicas.");
    }
}
