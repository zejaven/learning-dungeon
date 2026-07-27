import visual.VisualBroker;

public class Playground {
    public static void main(String[] args) {
        VisualBroker broker = new VisualBroker();

        // Topology first. A producer NEVER writes to a queue directly: it
        // publishes to an exchange, and a binding decides where the message lands.
        broker.declareExchange("orders", "direct");
        broker.declareQueue("orders.created");
        broker.bind("orders", "orders.created", "created");

        // A consumer subscribes. RabbitMQ pushes messages to it (no polling);
        // prefetch 1 means "never leave more than one unacked message with me".
        broker.subscribe("worker-1", "orders.created", 1);

        // publish -> route -> store in the queue -> push to the consumer.
        broker.publish("orders", "created", "m1", "order #1001");

        // The message is only *unacked* so far: it still belongs to the broker.
        // The ack is what finally deletes it.
        broker.ack("m1");

        System.out.println("published -> routed -> delivered -> acked");
    }
}
