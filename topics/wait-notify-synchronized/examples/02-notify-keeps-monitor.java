import visual.VisualMonitor;

public class Playground {
    public static void main(String[] args) {
        VisualMonitor mailbox = new VisualMonitor("mailbox");

        mailbox.enter("consumer");
        mailbox.waitOnCondition("consumer");

        mailbox.enter("producer");
        mailbox.setConditionReady("producer", true);
        mailbox.notifyOne("producer");

        // The consumer cannot continue until the producer exits synchronized.
        mailbox.exit("producer");
        mailbox.checkCondition("consumer");
        mailbox.exit("consumer");
    }
}
