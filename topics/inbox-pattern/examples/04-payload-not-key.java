import visual.VisualInbox;

public class Playground {
    public static void main(String[] args) {
        VisualInbox inbox = new VisualInbox("payments");

        // TRAP: deduplication is keyed by the stable, unique MESSAGE ID, not by
        // the payload. Two genuinely different events can carry the same body
        // (a customer really did buy the same item twice).
        inbox.receive("m1", "charge card", 100);   // processed
        inbox.receive("m2", "charge card", 100);   // different id -> processed, NOT a duplicate

        // Both are applied (total 200). If you deduped by payload you would
        // silently drop the second legitimate purchase.
        System.out.println("Same payload, different ids -> two separate effects.");
    }
}
