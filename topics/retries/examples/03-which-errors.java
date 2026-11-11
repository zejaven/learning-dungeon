import visual.VisualRetry;

public class Playground {
    public static void main(String[] args) {
        VisualRetry retry = new VisualRetry("catalog client");
        retry.maxAttempts(3).backoff(100, 2.0, 1_000);

        // 400 Bad Request: the same request will fail the same way. Do not retry.
        retry.script("400");
        retry.call("GET /products?price=abc");

        // 429 with Retry-After: the server says when to come back. Wait at least that long.
        retry.script("429:800", "200");
        retry.call("GET /products?page=2");
    }
}
