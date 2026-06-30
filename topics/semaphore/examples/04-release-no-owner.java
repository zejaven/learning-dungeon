import visual.VisualSemaphore;

public class Playground {
    public static void main(String[] args) {
        // Semaphore does not enforce ownership the way Lock does.
        VisualSemaphore binaryGate = new VisualSemaphore("binaryGate", 1);

        binaryGate.acquire("Worker");
        binaryGate.release("Watchdog");

        System.out.println("available permits = " + binaryGate.availablePermits());
    }
}
