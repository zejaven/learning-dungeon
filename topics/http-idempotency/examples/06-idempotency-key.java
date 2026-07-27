import visual.VisualIdempotency;
import visual.VisualIdempotency.Body;

public class Playground {
    public static void main(String[] args) {
        VisualIdempotency api = VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // The client names the intent BEFORE sending it, and repeats that name
        // on every retry of the same intent.
        Body order = Body.of("item", "mugs").and("qty", "1").and("status", "new");
        api.dropNextResponse();
        api.postWithKey("/orders", "key-8f3c", order);

        // Same timeout as example 3, same retry — but the server recognizes the
        // key and replays the stored answer instead of creating a second order.
        api.retry();

        // A genuinely new order carries a NEW key, even with an identical body:
        // the key is the identity of the intent, not a hash of the payload.
        api.postWithKey("/orders", "key-2a91", order);

        api.report();
        System.out.println("POST stays non-idempotent; this endpoint was made idempotent by hand.");
    }
}
