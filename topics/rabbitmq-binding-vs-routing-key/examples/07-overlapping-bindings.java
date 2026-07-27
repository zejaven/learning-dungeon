import visual.VisualRouter;

public class Playground {
    public static void main(String[] args) {
        VisualRouter router = new VisualRouter("logs", "topic");

        // Two binding keys on the SAME queue, both able to match one message.
        router.bind("audit", "order.#");
        router.bind("audit", "#.created");
        router.bind("billing", "order.paid");

        // Both audit bindings match, yet audit still receives exactly one copy.
        router.publish("order.created", "m1");

        // Here audit matches via 'order.#' and billing via its exact key.
        router.publish("order.paid", "m2");

        System.out.println("overlapping bindings never duplicate a message inside one queue");
    }
}
