import visual.VisualPushChannel;

public class Playground {
    public static void main(String[] args) {
        // An open connection is state, and state lives in exactly one process.
        VisualPushChannel stream = VisualPushChannel.sse("dashboard on instance A");
        stream.tick(1);
        stream.serverEvent("order-42 paid");

        // The next order is handled by instance B, which holds no connection
        // for this user and therefore has nothing to write to.
        stream.eventOnAnotherInstance("order-43 paid");

        // The fix is a second messaging system underneath the first one.
        stream.useSharedBus();
        stream.eventOnAnotherInstance("order-44 paid");
        stream.report();

        // Polling never had the problem: the news is in shared storage, so any
        // instance behind the load balancer can answer the next request.
        VisualPushChannel polled = VisualPushChannel.shortPolling("dashboard on any instance", 4);
        polled.eventOnAnotherInstance("order-45 paid");
        polled.tick(4);
        polled.report();

        System.out.println("Push needs fan-out; polling scales out for free.");
    }
}
