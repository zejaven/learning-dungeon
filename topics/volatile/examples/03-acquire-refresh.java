import visual.VisualVolatile;

public class Playground {
    public static void main(String[] args) {
        VisualVolatile mailbox = new VisualVolatile("acquire-demo", true);

        mailbox.writeData("Writer", 7);
        mailbox.writeReady("Writer", true);

        int beforeAcquire = mailbox.readData("Reader");
        mailbox.readReady("Reader");
        int afterAcquire = mailbox.readData("Reader");

        System.out.println("before = " + beforeAcquire + ", after = " + afterAcquire);
    }
}
