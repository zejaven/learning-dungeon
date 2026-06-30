import visual.VisualHappensBefore;

public class Playground {
    public static void main(String[] args) {
        VisualHappensBefore hb = new VisualHappensBefore("monitor-mailbox");

        hb.lock("Producer", "mailboxLock");
        hb.writePlain("Producer", "message", "approved");
        hb.unlock("Producer", "mailboxLock");

        hb.lock("Consumer", "mailboxLock");
        System.out.println("Consumer message = " + hb.readPlain("Consumer", "message"));
        hb.unlock("Consumer", "mailboxLock");
    }
}
