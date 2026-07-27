import visual.VisualHttpServer;
import visual.VisualHttpServer.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpServer api = VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // One exchange is one request (method + path + headers, sometimes a body)
        // and one response (status line + headers, sometimes a body). The server
        // remembers nothing between exchanges, so every request has to carry
        // everything needed to answer it.
        api.get("/orders");
        api.get("/orders/1");

        // Same protocol, same method, different status: the target decides.
        api.get("/orders/99");

        api.report();
        System.out.println("Three GETs, zero writes: reading is the one thing the protocol promises is free.");
    }
}
