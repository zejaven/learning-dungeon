import visual.VisualVolatile;

public class Playground {
    public static void main(String[] args) {
        VisualVolatile mailbox = new VisualVolatile("plain-mailbox", false);

        mailbox.writeData("Writer", 42);
        mailbox.writeReady("Writer", true);

        boolean ready = mailbox.readReady("Reader");
        int value = mailbox.readData("Reader");

        System.out.println("ready = " + ready + ", data = " + value);
    }
}
