import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        VisualCors cors = VisualCors.browser("https://app.example.com", "https://api.example.com",
                Policy.allowOrigin("https://app.example.com"));

        // Content-Type is on the response safelist, so it is readable already.
        cors.send(Request.get("/orders").readsHeader("Content-Type"));

        // A pagination header is not. The response body arrives fine, and
        // response.headers.get("X-Total-Count") quietly returns null.
        cors.send(Request.get("/orders").readsHeader("X-Total-Count"));

        // The server has to opt each custom header in by name.
        cors.reconfigure(Policy.allowOrigin("https://app.example.com")
                .exposeHeaders("X-Total-Count"));
        cors.send(Request.get("/orders").readsHeader("X-Total-Count"));

        cors.report();
        System.out.println("Reading the body and reading a header are two separate permissions.");
    }
}
