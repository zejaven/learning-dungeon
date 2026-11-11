import visual.VisualRetry;

public class Playground {
    public static void main(String[] args) {
        VisualRetry retry = new VisualRetry("checkout -> orders");

        // 4 attempts total; waits of 100, 200, 400 ms between them (capped at 1 s).
        retry.maxAttempts(4).backoff(100, 2.0, 1_000);

        // The orders service is briefly overloaded, then recovers.
        retry.script("503", "503", "200");

        boolean ok = retry.call("GET /orders/42");
        System.out.println("succeeded = " + ok);
    }
}
