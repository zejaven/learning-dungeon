import visual.VisualSemaphore;

public class Playground {
    public static void main(String[] args) {
        VisualSemaphore encoders = new VisualSemaphore("videoEncoders", 3);

        encoders.acquire("Job-A", 2);
        encoders.acquire("Job-B");
        encoders.acquire("Job-C");

        encoders.release("Job-A", 2);
        System.out.println("waiting jobs = " + encoders.queueLength());
    }
}
