import visual.VisualMonitor;

public class Playground {
    public static void main(String[] args) {
        VisualMonitor mailbox = new VisualMonitor("mailbox");

        mailbox.enter("producer");
        mailbox.notifyOne("producer");
        mailbox.exit("producer");

        mailbox.enter("consumer");
        if (!mailbox.checkCondition("consumer")) {
            // The earlier notify() was not remembered.
            mailbox.waitOnCondition("consumer");
        }
    }
}
