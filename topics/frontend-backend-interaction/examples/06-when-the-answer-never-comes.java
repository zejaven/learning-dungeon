import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");
        app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));

        // No connection: the request never leaves. There is no status code at
        // all -- and no status code is not the same as 500.
        app.networkDown();
        app.call(Request.get("/api/employees"));
        app.networkUp();

        // Worse: the request arrives, the handler writes the row, and the
        // ANSWER is lost. The frontend sees exactly what it saw before.
        app.dropNextResponse();
        app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());

        // So the client retries, as clients do. The server cannot tell this
        // retry from a genuine second hire.
        app.call(Request.post("/api/employees").json("name", "Iris Vogel").authenticated());

        // Same failure, but this time the client named the write before
        // sending it -- so the repeat is recognisable.
        app.dropNextResponse();
        app.call(Request.post("/api/employees")
                .json("name", "Noor Haddad").authenticated().idempotencyKey("hire-77"));
        app.call(Request.post("/api/employees")
                .json("name", "Noor Haddad").authenticated().idempotencyKey("hire-77"));

        app.report();
        System.out.println("Two people named Iris Vogel, one named Noor Haddad.");
    }
}
