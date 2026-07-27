import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        // A policy that allows the origin but forgot half the surface of the API.
        VisualCors cors = VisualCors.browser("https://app.example.com", "https://api.example.com",
                Policy.allowOrigin("https://app.example.com")
                        .allowMethods("GET", "POST")
                        .allowHeaders("Content-Type"));

        // DELETE is not in Access-Control-Allow-Methods.
        cors.send(Request.delete("/orders/42"));

        // The method is fine, but the browser also asks about every header that
        // is not safelisted -- and Authorization is not.
        cors.send(Request.post("/orders").json().header("Authorization", "Bearer t0ken"));

        cors.report();
        System.out.println("A failed preflight never reaches the handler: no writes, no side effects.");
    }
}
