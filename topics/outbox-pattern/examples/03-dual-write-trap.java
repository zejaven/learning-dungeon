import visual.VisualOutbox;

public class Playground {
    public static void main(String[] args) {
        VisualOutbox outbox = new VisualOutbox("orders");

        // THE PROBLEM the outbox exists to prevent: a dual write. The service
        // commits the order to the database, then as a SEPARATE step tries to
        // publish to the broker. If that second step crashes, the order is saved
        // but the event is gone — and with no outbox there is nothing to retry.
        outbox.placeOrderDualWrite("o1", 100);

        System.out.println("Dual write: order committed but the event was lost.");
    }
}
