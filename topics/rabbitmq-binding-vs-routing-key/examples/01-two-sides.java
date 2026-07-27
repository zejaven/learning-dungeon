import visual.VisualRouter;

public class Playground {
    public static void main(String[] args) {
        // A direct exchange compares the two keys for exact equality.
        VisualRouter router = new VisualRouter("orders", "direct");

        // The BINDING key is chosen once, by whoever owns the queue.
        router.bind("invoices", "order.paid");

        // A queue with no binding at all is simply unreachable.
        router.declareQueue("shipping");

        // The ROUTING key is stamped on every message, by the producer.
        router.publish("order.paid", "m1");       // equal -> the binding matches
        router.publish("order.cancelled", "m2");  // no binding key equals this -> unroutable

        System.out.println("binding key: on the binding. routing key: on the message.");
    }
}
