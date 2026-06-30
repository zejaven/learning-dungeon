import visual.VisualSemaphore;

public class Playground {
    public static void main(String[] args) {
        VisualSemaphore gate = new VisualSemaphore("reportSlot", 1);

        gate.acquire("Worker");

        if (!gate.tryAcquire("Metrics")) {
            System.out.println("Metrics skips optional work instead of waiting.");
        }

        gate.release("Worker");
        gate.tryAcquire("Metrics");
    }
}
