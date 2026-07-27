import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        // Three stateless replicas, 6 requests per tick of application capacity —
        // and one primary database behind them that can only start 3 per tick.
        VisualLoadBalancer pool = VisualLoadBalancer.behindBalancer("POST /checkout", 3, 4, 2, 6);
        pool.sharedDependency("one PostgreSQL primary", 3);
        pool.traffic(6, 10);

        // The usual reflex: add another replica. Watch the numbers not move.
        pool.addNode();
        pool.traffic(6, 10);
        pool.report();

        // The same traffic priced across four topologies.
        VisualLoadBalancer.compare();

        System.out.println("Ask what the constraint is before asking how many instances.");
    }
}
