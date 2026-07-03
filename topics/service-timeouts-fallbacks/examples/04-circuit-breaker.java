import visual.VisualServiceCalls;

public class Playground {
    public static void main(String[] args) {
        VisualServiceCalls calls = new VisualServiceCalls("gateway");

        calls.deadline(200)
                .service("recommendations", 5_000, false);

        calls.callWithCircuitBreaker("recommendations", 80, 2, "CACHE:POPULAR_PRODUCTS");
        calls.callWithCircuitBreaker("recommendations", 80, 2, "CACHE:POPULAR_PRODUCTS");
        calls.callWithCircuitBreaker("recommendations", 80, 2, "CACHE:POPULAR_PRODUCTS");
        calls.completeResponse("FAST_DEGRADED_RESPONSE");
    }
}
