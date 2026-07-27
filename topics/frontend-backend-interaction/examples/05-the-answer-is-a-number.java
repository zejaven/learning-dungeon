import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");
        app.call(Request.post("/api/sessions").json("user", "ada").and("password", "secret"));

        // 400: the request was understood and refused. The body says WHICH
        // field and WHY in a form the frontend can act on.
        app.call(Request.post("/api/employees").json("role", "engineer").authenticated());

        // 404 on a route that exists: the question was fine, the thing is not
        // there. A different answer from "no such endpoint".
        app.call(Request.get("/api/employees/99"));

        // 404 with nothing matched at all -- a path is compared as text.
        app.call(Request.get("/api/employee/1"));

        // 500: the server broke. The frontend cannot fix it, only retry.
        app.call(Request.get("/api/reports/payroll"));

        app.report();
        System.out.println("Four different outcomes, four codes, one protocol.");
    }
}
