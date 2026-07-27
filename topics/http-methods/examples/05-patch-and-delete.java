import visual.VisualHttpServer;
import visual.VisualHttpServer.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpServer api = VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // PATCH carries only the change, not the whole representation: fields the
        // body never mentions keep their values.
        api.patch("/orders/1", Body.of("status", "paid"));

        // DELETE removes the resource at a URL. 204 means "done, nothing to send".
        api.delete("/orders/2");

        // The retry finds nothing to delete and answers 404 -- but the state it
        // leaves behind is exactly the state the first call left.
        api.delete("/orders/2");

        api.report();
        System.out.println("Different status codes, identical end state: that is what idempotent means.");
    }
}
