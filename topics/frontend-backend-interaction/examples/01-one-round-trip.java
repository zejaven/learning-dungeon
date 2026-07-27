import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        // Two separate programs. The browser has the screen and no data; the
        // server has the data and no screen. They share no memory, no objects
        // and no method calls -- only messages.
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");

        // One round trip. Watch the four moments it is made of: the frontend
        // writes a text request, the promise goes pending, the router picks a
        // handler, and the answer comes back as text to be parsed and rendered.
        app.call(Request.get("/api/employees"));

        app.report();
        System.out.println("Everything on the screen arrived through that one message.");
    }
}
