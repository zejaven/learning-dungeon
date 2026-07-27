import visual.VisualRouter;

public class Playground {
    public static void main(String[] args) {
        VisualRouter router = new VisualRouter("orders", "topic");
        router.bind("paid", "order.paid");
        router.bind("shipped", "order.shipped");

        // The trap: a producer hoping one wildcard key will reach both queues.
        // In a routing key '*' is an ordinary character, so this matches nothing.
        router.publish("order.*", "m1");

        // Wildcards belong on the binding side. Bind a pattern instead...
        router.bind("all-orders", "order.*");

        // ...and publish concrete keys: now one queue receives both events.
        router.publish("order.paid", "m2");
        router.publish("order.shipped", "m3");

        System.out.println("only a binding key may be a pattern");
    }
}
