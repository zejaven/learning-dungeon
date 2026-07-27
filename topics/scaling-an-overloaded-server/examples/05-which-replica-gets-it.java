import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        // Three replicas, 2 requests per tick each, taking 4 requests per tick.
        // Plenty of capacity — as long as all three are equally healthy.
        VisualLoadBalancer roundRobin = VisualLoadBalancer.behindBalancer("POST /checkout", 3, 4, 2, 6);

        // One node slows down: a long GC pause, a cold cache, a noisy neighbour.
        roundRobin.degrade("app-2", 8);
        roundRobin.traffic(4, 16);
        roundRobin.report();

        // The same fleet, the same fault, routed by observed load instead of by turn.
        VisualLoadBalancer leastBusy = VisualLoadBalancer.behindBalancer("POST /checkout", 3, 4, 2, 6);
        leastBusy.strategy(VisualLoadBalancer.Strategy.LEAST_BUSY);
        leastBusy.degrade("app-2", 8);
        leastBusy.traffic(4, 16);
        leastBusy.report();

        System.out.println("Equal shares to unequal nodes is not balance.");
    }
}
