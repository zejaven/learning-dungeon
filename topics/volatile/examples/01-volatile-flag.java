import visual.VisualVolatile;

public class Playground {
    public static void main(String[] args) {
        VisualVolatile mailbox = new VisualVolatile("mailbox", true);

        mailbox.writeData("Writer", 42);
        mailbox.writeReady("Writer", true);

        if (mailbox.readReady("Reader")) {
            int value = mailbox.readData("Reader");
            System.out.println("Reader received data = " + value);
        }
    }
}
