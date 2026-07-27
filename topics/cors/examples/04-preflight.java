import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        VisualCors cors = VisualCors.browser("https://app.example.com", "https://api.example.com",
                Policy.allowOrigin("https://app.example.com")
                        .allowMethods("GET", "POST", "PUT")
                        .allowHeaders("Content-Type"));

        // Safelisted method, safelisted headers -> sent immediately.
        cors.send(Request.get("/orders"));

        // Content-Type: application/json is NOT on the safelist, so this POST is
        // something no HTML form could have produced. The browser asks first.
        cors.send(Request.post("/orders").json());

        // A non-simple method preflights on its own, whatever the body is.
        cors.send(Request.put("/orders/42").json());

        cors.report();
        System.out.println("JSON bodies and PUT/PATCH/DELETE are what turn one request into two.");
    }
}
