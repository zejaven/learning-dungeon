import visual.VisualRetry;

public class Playground {
    public static void main(String[] args) {
        VisualRetry retry = new VisualRetry("20 mobile clients -> api");
        retry.maxAttempts(3).backoff(200, 2.0, 2_000);

        // A 1-second blip: every client fails at t=0 and retries on the same schedule.
        int peakWithout = retry.herd(20, 100);

        // Same backoff, but each client picks a random wait in [0, delay].
        retry.jitter("FULL");
        int peakWith = retry.herd(20, 100);

        System.out.println("peak per 100 ms without jitter = " + peakWithout);
        System.out.println("peak per 100 ms with full jitter = " + peakWith);
    }
}
