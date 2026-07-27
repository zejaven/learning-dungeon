import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");

        // The server has news and no way to deliver it: HTTP conversations are
        // always started by the client, and the tab has no address.
        app.pushFromServer("order-42 shipped");

        // Workaround one: ask again and again. Every "nothing yet" costs a
        // full round trip on both sides.
        app.pollForNews(3);

        // Workaround two: upgrade the connection once and keep it open, so
        // either side may send whenever it has something to say.
        app.openSocket();
        app.pushFromServer("order-42 shipped");

        app.report();
        System.out.println("Polling asks; a socket lets the server tell.");
    }
}
