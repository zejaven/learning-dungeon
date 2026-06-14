import visual.VisualOutbox;

public class Playground {
    public static void main(String[] args) {
        VisualOutbox outbox = new VisualOutbox("orders");

        // Several business transactions each stage their own event. The outbox
        // preserves insertion order, which lets the relay publish events in the
        // same order the changes were committed.
        outbox.placeOrder("o1", 100);
        outbox.placeOrder("o2", 250);
        outbox.placeOrder("o3", 75);

        // One relay run drains all PENDING rows to the broker, in order.
        outbox.relayPublish();

        System.out.println("Three orders staged, relay published all three in order.");
    }
}
