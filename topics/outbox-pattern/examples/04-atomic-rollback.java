import visual.VisualOutbox;

public class Playground {
    public static void main(String[] args) {
        VisualOutbox outbox = new VisualOutbox("orders");

        // The business write and the outbox insert share ONE transaction, so if
        // the transaction fails everything rolls back together: no order is
        // saved AND no event is staged. You never publish an event for a change
        // that did not actually happen.
        outbox.placeOrderFailing("o1", 100);

        // The relay finds nothing PENDING, so it publishes nothing.
        outbox.relayPublish();

        System.out.println("Transaction rolled back: no order, no staged event.");
    }
}
