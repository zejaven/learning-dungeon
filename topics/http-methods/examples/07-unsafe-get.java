import visual.VisualHttpServer;
import visual.VisualHttpServer.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpServer api = VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"),
                Body.of("item", "spoons").and("qty", "4").and("status", "new"));

        // Cancelling was exposed as a link, so it became a GET. The method still
        // promises "safe"; the handler behind it writes.
        api.mutatingGet("/orders/1/cancel", "/orders/1", Body.of("status", "cancelled"));

        // Nobody clicked this one: a link prefetcher followed it, exactly as the
        // safety promise allows it to.
        api.mutatingGet("/orders/2/cancel", "/orders/2", Body.of("status", "cancelled"));

        // The same intent through a method that admits it writes. No cache, proxy
        // or crawler will ever repeat this one on its own.
        api.patch("/orders/3", Body.of("status", "cancelled"));

        api.report();
        System.out.println("Two orders cancelled by machines: 'safe' is a promise the handler has to keep.");
    }
}
