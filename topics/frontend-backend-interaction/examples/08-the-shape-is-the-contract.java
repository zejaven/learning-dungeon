import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");

        app.call(Request.get("/api/employees/1"));
        app.readField("name");

        // A tidy-up on the backend: one field gets a better name. Every server
        // test still passes -- the rename is invisible inside one process.
        app.renameField("name", "fullName");

        // The frontend in the user's browser was not redeployed. It asks the
        // same question, gets a 200, and reads a field that no longer exists.
        app.call(Request.get("/api/employees/1"));
        app.readField("name");

        app.report();
        System.out.println("200 OK, clean logs, and 'undefined' on the screen.");
    }
}
