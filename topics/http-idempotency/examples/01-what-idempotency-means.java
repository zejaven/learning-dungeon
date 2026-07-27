import visual.VisualIdempotency;
import visual.VisualIdempotency.Body;

public class Playground {
    public static void main(String[] args) {
        VisualIdempotency api = VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // Idempotent: N identical calls leave the same state as one call.
        // A read is the trivial case — it changes nothing, so repeating it is free.
        api.get("/orders/1");

        // PUT carries the TARGET STATE ("let this URL hold exactly this"), so
        // saying it three times describes the same world three times.
        Body shipped = Body.of("item", "tea").and("qty", "2").and("status", "shipped");
        api.put("/orders/1", shipped);
        api.put("/orders/1", shipped);
        api.put("/orders/1", shipped);

        api.report();
        System.out.println("Three calls, one effect: the state after N calls is the state after one.");
    }
}
