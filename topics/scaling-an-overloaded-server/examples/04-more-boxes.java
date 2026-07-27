import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        VisualLoadBalancer service = VisualLoadBalancer.singleServer("POST /checkout", 4, 2, 12);

        // The same overload as the vertical example started from.
        service.traffic(6, 6);

        // Horizontal scaling. The first replica also puts a load balancer in front,
        // because clients now need one address that fans out to many instances.
        service.addNode();
        service.addNode();

        // Capacity is 6 per tick across three replicas, and it changed while the
        // service kept serving traffic.
        service.traffic(4, 8);
        service.tick(10);

        service.report();
        System.out.println("Capacity became a deployment decision instead of a hardware one.");
    }
}
