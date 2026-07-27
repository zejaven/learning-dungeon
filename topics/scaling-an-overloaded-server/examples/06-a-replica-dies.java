import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        // Three replicas and a balancer that was never told to check on them.
        VisualLoadBalancer blind = VisualLoadBalancer.behindBalancer("POST /checkout", 3, 4, 2, 6);
        blind.traffic(3, 4);
        blind.failNode("app-2");
        blind.traffic(3, 6);
        blind.report();

        // The same failure, with health checks that notice after 2 ticks. Traffic is
        // 5 per tick against 6 per tick of capacity — comfortable with three
        // replicas, and not comfortable with two.
        VisualLoadBalancer checked = VisualLoadBalancer.behindBalancer("POST /checkout", 3, 4, 2, 6);
        checked.healthChecks(2);
        checked.traffic(5, 4);
        checked.failNode("app-2");
        checked.traffic(5, 8);
        checked.report();

        System.out.println("Health checks bound the damage of a crash. They do not prevent it.");
    }
}
