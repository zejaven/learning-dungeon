import visual.VisualSemaphore;

public class Playground {
    public static void main(String[] args) {
        // One permit makes this semaphore behave like a single-slot gate.
        VisualSemaphore printer = new VisualSemaphore("printer", 1);

        printer.acquire("T1");
        boolean t2Entered = printer.acquire("T2");
        System.out.println("T2 entered immediately = " + t2Entered);

        printer.release("T1");
    }
}
