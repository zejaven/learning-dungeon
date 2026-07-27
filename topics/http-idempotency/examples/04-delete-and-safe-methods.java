import visual.VisualIdempotency;
import visual.VisualIdempotency.Body;

public class Playground {
    public static void main(String[] args) {
        VisualIdempotency api = VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // Safe methods change nothing, so they are idempotent for free.
        api.get("/orders/1");
        api.head("/orders/1");

        // DELETE removes the resource. The response to the retry is different...
        api.dropNextResponse();
        api.delete("/orders/2");
        api.retry();

        // ...but "gone" is "gone": the state after two calls is the state after one.
        api.report();
        System.out.println("204 then 404, same state: idempotency is about state, not status codes.");
    }
}
