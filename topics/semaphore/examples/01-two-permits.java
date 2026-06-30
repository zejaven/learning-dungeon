import visual.VisualSemaphore;

public class Playground {
    public static void main(String[] args) {
        // Two permits mean two workers may enter the guarded resource together.
        VisualSemaphore gate = new VisualSemaphore("dbConnections", 2);

        gate.acquire("Worker-1");
        gate.acquire("Worker-2");

        System.out.println("available permits = " + gate.availablePermits());
    }
}
