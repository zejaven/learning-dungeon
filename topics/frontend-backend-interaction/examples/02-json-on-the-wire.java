import visual.VisualClientServer;
import visual.VisualClientServer.Request;

public class Playground {
    public static void main(String[] args) {
        VisualClientServer app = VisualClientServer.open("shop-web (browser)", "shop-api (server)");

        // Ask for one employee. On the server this is an Employee entity with a
        // LocalDate and a BigDecimal in it.
        app.call(Request.get("/api/employees/1"));

        // Nothing of the sort arrived. Look at what each field became on the
        // way: JSON has strings, numbers, booleans, null, arrays and objects --
        // and that is the whole type system the two sides get to share.
        app.inspectPayload();

        app.report();
        System.out.println("The browser holds a copy shaped like the entity, not the entity.");
    }
}
