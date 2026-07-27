import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        // A backend that has never been configured for CORS. It is not hostile
        // and it is not broken -- it simply never says who may read its answers.
        VisualCors cors = VisualCors.browser(
                "https://app.example.com", "https://api.example.com", Policy.none());

        // POST with a form content type is a "simple" request: a plain <form>
        // could have sent exactly this, so the browser does not ask permission
        // first. It sends it, the handler runs, and only the RESPONSE is hidden.
        cors.send(Request.post("/orders").formEncoded());

        cors.report();
        System.out.println("The order was created. The browser only hid the receipt.");
    }
}
