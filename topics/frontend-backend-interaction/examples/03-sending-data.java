import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");

        // Reading was open to anyone; writing is not. The server does not know
        // who is asking, because the previous requests told it nothing.
        app.call(Request.post("/api/employees").json("name", "Iris Vogel").and("role", "engineer"));

        // Sign in once: the answer is a token the frontend keeps for itself.
        app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));

        // The same write, now carrying that token in an Authorization header.
        // The id in the answer is the only way the frontend can learn it.
        app.call(Request.post("/api/employees")
                .json("name", "Iris Vogel")
                .and("role", "engineer")
                .authenticated());

        app.report();
        System.out.println("A create is an exchange: the server names the thing it created.");
    }
}
