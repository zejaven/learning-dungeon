import visual.VisualHttpServer;
import visual.VisualHttpServer.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpServer api = VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // POST hands a body to a collection and lets the SERVER decide the URL of
        // whatever it creates. The new address comes back in the Location header.
        api.post("/orders", Body.of("item", "mugs").and("qty", "1").and("status", "new"));

        // The response was lost on the way back, so the client "just retries".
        // Nothing in POST says the second call is the same call as the first.
        api.post("/orders", Body.of("item", "mugs").and("qty", "1").and("status", "new"));

        api.report();
        System.out.println("One customer, one intent, two orders: POST is not idempotent.");
    }
}
