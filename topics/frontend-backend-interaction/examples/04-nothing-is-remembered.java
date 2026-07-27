import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");

        app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));

        // Signing in did not put the user "into" the server. The token has to
        // ride along on every single request, over and over.
        app.call(Request.delete("/api/employees/2").authenticated());

        // Leave it off once and the very next call is anonymous again.
        app.call(Request.delete("/api/employees/1"));

        // F5. The frontend's memory is wiped and has to re-fetch everything;
        // the server never noticed, because it had nothing about this user.
        app.reloadPage();
        app.call(Request.get("/api/employees").authenticated());

        app.report();
        System.out.println("Sessions stored on the server: 0. Identity travels in the request.");
    }
}
