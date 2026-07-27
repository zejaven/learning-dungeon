import visual.VisualLoadBalancer;

public class Playground {
    public static void main(String[] args) {
        // The same server as before: still exactly 2 requests per tick of capacity.
        VisualLoadBalancer server = VisualLoadBalancer.singleServer("POST /checkout", 4, 2, 12);

        // Clients give up after 4 ticks. Note that the queue holds 12 requests,
        // which at 2 per tick is a 6-tick wait — deeper than the timeout.
        server.clientTimeout(4);

        // Three times the capacity, arriving every tick.
        server.traffic(6, 10);

        server.report();
        System.out.println("The code did not get slower. The wait got longer.");
    }
}
