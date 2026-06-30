import visual.VisualMonitor;

public class Playground {
    public static void main(String[] args) {
        VisualMonitor mailbox = new VisualMonitor("mailbox");

        try {
            mailbox.waitOnCondition("worker");
        } catch (IllegalStateException ex) {
            System.out.println(ex.getMessage());
        }

        mailbox.enter("worker");
        mailbox.waitOnCondition("worker");
    }
}
