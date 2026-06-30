import visual.VisualMonitor;

public class Playground {
    public static void main(String[] args) {
        VisualMonitor mailbox = new VisualMonitor("mailbox");

        mailbox.enter("consumerA");
        mailbox.waitOnCondition("consumerA");

        mailbox.enter("consumerB");
        mailbox.waitOnCondition("consumerB");

        mailbox.enter("producer");
        mailbox.setConditionReady("producer", true);
        mailbox.notifyAllWaiters("producer");
        mailbox.exit("producer");

        mailbox.checkCondition("consumerA");
        mailbox.exit("consumerA");

        mailbox.checkCondition("consumerB");
        mailbox.exit("consumerB");
    }
}
