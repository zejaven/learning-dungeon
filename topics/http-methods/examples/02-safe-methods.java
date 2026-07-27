import visual.VisualHttpServer;
import visual.VisualHttpServer.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpServer api = VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // GET, HEAD and OPTIONS are the safe methods: they are not supposed to
        // change anything on the server. That promise is what lets a client reuse
        // the answer instead of asking again.
        api.get("/orders/1");
        api.get("/orders/1");

        // HEAD is the same request without the body: use it to check that
        // something exists, how big it is, or whether your copy is stale.
        api.head("/orders/1");

        // OPTIONS asks what a URL supports instead of touching it.
        api.options("/orders");

        api.report();
        System.out.println("Four requests, nothing written: safe methods are why caches and prefetch can exist.");
    }
}
