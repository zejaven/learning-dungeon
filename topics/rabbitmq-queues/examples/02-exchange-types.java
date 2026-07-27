import visual.VisualBroker;

public class Playground {
    public static void main(String[] args) {
        VisualBroker broker = new VisualBroker();

        // A direct exchange delivers to queues whose binding key EQUALS the
        // routing key. This is the "task queue" shape.
        broker.declareExchange("orders", "direct");
        broker.declareQueue("orders.created");
        broker.declareQueue("orders.cancelled");
        broker.bind("orders", "orders.created", "created");
        broker.bind("orders", "orders.cancelled", "cancelled");

        broker.publish("orders", "created", "m1", "order #1");

        // No binding matches "shipped": the message is unroutable and the broker
        // throws it away. Publishing succeeds anyway — the producer is not told.
        broker.publish("orders", "shipped", "m2", "order #2");

        // A fanout exchange ignores the routing key and gives EVERY bound queue
        // its own independent copy. This is the "publish/subscribe" shape.
        broker.declareExchange("events", "fanout");
        broker.declareQueue("audit");
        broker.declareQueue("search-index");
        broker.bind("events", "audit");
        broker.bind("events", "search-index");

        broker.publish("events", "ignored-key", "m3", "user updated");

        System.out.println("direct = one queue, fanout = a copy per queue");
    }
}
