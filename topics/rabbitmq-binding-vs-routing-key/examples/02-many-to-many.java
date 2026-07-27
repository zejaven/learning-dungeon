import visual.VisualRouter;

public class Playground {
    public static void main(String[] args) {
        VisualRouter router = new VisualRouter("orders", "direct");

        // Two queues can share one binding key: both are interested in the same event.
        router.bind("billing", "order.paid");
        router.bind("analytics", "order.paid");

        // One queue can hold several bindings: billing wants two kinds of event.
        router.bind("billing", "order.refunded");

        router.publish("order.paid", "m1");      // two queues match -> two copies
        router.publish("order.refunded", "m2");  // only billing matches -> one copy

        System.out.println("binding keys and queues are many-to-many");
    }
}
