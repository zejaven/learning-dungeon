import visual.VisualIdempotency;
import visual.VisualIdempotency.Body;

public class Playground {
    public static void main(String[] args) {
        VisualIdempotency api = VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // POST means "create a subordinate resource under this collection".
        // Nothing in that sentence points at a resource that already exists.
        api.dropNextResponse();
        Body order = Body.of("item", "mugs").and("qty", "1").and("status", "new");
        api.post("/orders", order);

        // Exactly the same retry as the previous example, exactly the same bytes
        // on the wire — and a second, independent order.
        api.retry();

        api.report();
        System.out.println("One customer, one intent, two orders: POST is not idempotent.");
    }
}
