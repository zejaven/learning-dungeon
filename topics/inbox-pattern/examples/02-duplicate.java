import visual.VisualInbox;

public class Playground {
    public static void main(String[] args) {
        VisualInbox inbox = new VisualInbox("payments");

        // The broker guarantees at-least-once delivery, so the SAME message id
        // can be delivered twice (e.g. the consumer crashed before ack-ing).
        inbox.receive("m1", "charge card", 100);   // processed: total -> 100
        inbox.receive("m1", "charge card", 100);   // duplicate: skipped, total stays 100

        // The inbox dedup check made consumption idempotent: the card is
        // charged once even though the message was delivered twice.
        System.out.println("Delivered twice, charged once.");
    }
}
