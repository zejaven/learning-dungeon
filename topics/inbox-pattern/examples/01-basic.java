import visual.VisualInbox;

public class Playground {
    public static void main(String[] args) {
        // VisualInbox is a teaching model of the transactional inbox /
        // idempotent consumer pattern. The inbox table remembers which message
        // ids have already been processed.
        VisualInbox inbox = new VisualInbox("payments");

        // A brand-new message id: it is recorded in the inbox and its effect
        // (charge 100) is applied in the same local transaction.
        inbox.receive("m1", "charge card", 100);

        System.out.println("Processed the first message exactly once.");
    }
}
