import visual.VisualMonitor;

public class Playground {
    public static void main(String[] args) {
        VisualMonitor mailbox = new VisualMonitor("mailbox");

        mailbox.enter("consumer");
        if (!mailbox.checkCondition("consumer")) {
            mailbox.waitOnCondition("consumer");
        }

        // The consumer released the monitor, so the producer can enter now.
        mailbox.enter("producer");
    }
}
