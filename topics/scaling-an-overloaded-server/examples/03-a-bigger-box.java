import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        VisualLoadBalancer server = VisualLoadBalancer.singleServer("POST /checkout", 4, 2, 12);

        // Drowning at 2 requests per tick.
        server.traffic(6, 6);

        // Vertical scaling: the same one box, with three times the slots.
        server.scaleUp(12);

        // Traffic below the new ceiling, so the backlog can actually drain.
        server.traffic(4, 10);

        // And then the box dies. Nothing about the topology ever changed.
        server.failNode("app-1");

        server.report();
        System.out.println("A bigger box moves the wall. It does not add a second box.");
    }
}
