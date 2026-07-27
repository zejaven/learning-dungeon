import visual.VisualHttpServer;
import visual.VisualHttpServer.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpServer api = VisualHttpServer.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));

        // A collection URL and an item URL support different methods. Aiming a
        // method at the wrong one is a 405, and the Allow header says what would
        // have worked.
        api.delete("/orders");
        api.options("/orders");

        // POST creates something inside a collection, so it means nothing here.
        api.post("/orders/1", Body.of("item", "tea").and("qty", "1"));

        // 404 is the other failure: the method was fine, the URL is not there.
        api.get("/orders/99");

        api.report();
        System.out.println("405 = wrong method for this URL, 404 = no such URL, 501 = server has no such method.");
    }
}
