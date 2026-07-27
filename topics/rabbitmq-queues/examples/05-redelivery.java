import visual.VisualBroker;

public class Playground {
    public static void main(String[] args) {
        VisualBroker broker = new VisualBroker();

        broker.declareExchange("orders", "direct");
        broker.declareQueue("orders.created");
        broker.bind("orders", "orders.created", "created");
        broker.subscribe("worker-1", "orders.created", 1);

        broker.publish("orders", "created", "m1", "charge card 100");

        // The worker charged the card and then the pod was killed — the ack never
        // reached the broker. An unacked message is not lost: it goes back to the
        // head of its queue.
        broker.crashConsumer("worker-1");

        // The replacement worker gets the SAME message with redelivered = true.
        // The broker cannot know whether the work already happened, so it retries:
        // this is at-least-once delivery, and it is why handlers must be idempotent.
        broker.subscribe("worker-2", "orders.created", 1);
        broker.ack("m1");

        System.out.println("delivered twice, so the card can be charged twice");
    }
}
