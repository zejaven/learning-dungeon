import visual.VisualInbox;

public class Playground {
    public static void main(String[] args) {
        VisualInbox inbox = new VisualInbox("payments");

        inbox.receive("m1", "charge card", 100);   // processed: total -> 100

        // The inbox table cannot grow forever, so old ids are purged on a
        // retention policy. TRAP: here we keep nothing, so m1's id is forgotten.
        inbox.cleanup(0);

        // A late at-least-once redelivery of m1 now looks brand new and is
        // processed AGAIN (total -> 200). The retention window must be longer
        // than the broker's maximum redelivery delay.
        inbox.receive("m1", "charge card", 100);

        System.out.println("Purged too early -> the duplicate was reprocessed.");
    }
}
