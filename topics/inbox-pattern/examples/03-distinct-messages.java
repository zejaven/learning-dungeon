import visual.VisualInbox;

public class Playground {
    public static void main(String[] args) {
        VisualInbox inbox = new VisualInbox("payments");

        // Three different message ids are three different messages. Each one is
        // new, so each is recorded and applied. The inbox table grows.
        inbox.receive("m1", "charge card", 100);
        inbox.receive("m2", "issue refund", 30);
        inbox.receive("m3", "charge card", 70);

        System.out.println("Three distinct messages, three effects applied.");
    }
}
