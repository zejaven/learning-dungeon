import visual.VisualHttpServer;
import visual.VisualHttpServer.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpServer api = VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // PUT says "let this URL hold exactly this representation". The client
        // names the URL, and the body is the whole target state.
        api.put("/orders/1", Body.of("item", "tea").and("qty", "5").and("status", "paid"));

        // The same sentence said twice describes the same world, so the repeat is
        // a no-op. That is what makes PUT safe to retry after a timeout.
        api.put("/orders/1", Body.of("item", "tea").and("qty", "5").and("status", "paid"));

        // Because the client picks the URL, PUT can also create what is not there.
        api.put("/orders/9", Body.of("item", "spoons").and("qty", "4").and("status", "new"));

        api.report();
        System.out.println("Two identical PUTs, one result: idempotency is about the final state.");
    }
}
