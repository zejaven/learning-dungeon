import visual.VisualMonitor;

public class Playground {
    public static void main(String[] args) {
        VisualMonitor mailbox = new VisualMonitor("mailbox");

        mailbox.enter("consumer");
        while (!mailbox.checkCondition("consumer")) {
            mailbox.waitOnCondition("consumer");

            // The JVM is allowed to wake a thread without notify().
            mailbox.spuriousWakeup("consumer");
            break;
        }

        while (!mailbox.checkCondition("consumer")) {
            mailbox.waitOnCondition("consumer");
            mailbox.enter("producer");
            mailbox.setConditionReady("producer", true);
            mailbox.notifyOne("producer");
            mailbox.exit("producer");
        }

        mailbox.checkCondition("consumer");
        mailbox.exit("consumer");
    }
}
