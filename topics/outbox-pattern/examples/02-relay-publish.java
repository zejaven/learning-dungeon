import visual.VisualOutbox;

public class Playground {
    public static void main(String[] args) {
        VisualOutbox outbox = new VisualOutbox("orders");

        // The business transaction only stages the event; it does NOT talk to
        // the broker. The outbox row starts PENDING.
        outbox.placeOrder("o1", 100);

        // A separate relay (a poller, or change-data-capture) reads PENDING
        // rows, publishes them to the broker, and marks them PUBLISHED. This
        // decouples the commit from the (possibly slow or down) broker.
        outbox.relayPublish();

        System.out.println("Relay published the staged event to the broker.");
    }
}
