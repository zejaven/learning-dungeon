import visual.VisualRetry;

public class Playground {
    public static void main(String[] args) {
        VisualRetry retry = new VisualRetry("shop -> payments");
        retry.maxAttempts(3).backoff(100, 2.0, 1_000).attemptTimeout(300);

        // The first attempt times out: the payment may or may not have been charged.
        retry.script("TIMEOUT", "201");

        // A plain POST is not idempotent: retrying could charge the card twice.
        retry.call("POST /payments");

        // With an idempotency key the server deduplicates, so the retry is safe.
        retry.callWithKey("POST /payments", "order-42-payment");
    }
}
