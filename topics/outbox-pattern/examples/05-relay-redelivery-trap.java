import visual.VisualOutbox;

public class Playground {
    public static void main(String[] args) {
        VisualOutbox outbox = new VisualOutbox("orders");

        outbox.placeOrder("o1", 100);   // staged: outbox row PENDING

        // TRAP: the relay sends the event to the broker, then crashes BEFORE it
        // marks the row PUBLISHED. The broker already has the event, yet the row
        // is still PENDING.
        outbox.relayCrashAfterSend();

        // On the next poll the relay sends the same event again -> the broker
        // receives a DUPLICATE. The relay is at-least-once, so consumers must
        // dedup (this is exactly what the Inbox pattern is for).
        outbox.relayPublish();

        System.out.println("Relay crashed before marking -> the event was redelivered.");
    }
}
