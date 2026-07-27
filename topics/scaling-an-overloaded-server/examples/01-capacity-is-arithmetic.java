import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        // One instance: 4 concurrent slots, 2 ticks of work per request, room for
        // 12 waiting requests. Throughput is therefore 4 / 2 = 2 requests per tick.
        VisualLoadBalancer server = VisualLoadBalancer.singleServer("POST /checkout", 4, 2, 12);

        // Traffic stays below the ceiling, so the surplus that causes every other
        // symptom simply does not exist.
        server.traffic(2, 8);

        // Let the last requests in flight finish.
        server.tick(3);

        server.report();
        System.out.println("Below capacity, latency is the work and nothing else.");
    }
}
