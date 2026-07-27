import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        // "Open to everyone" is the configuration people reach for first.
        VisualCors cors = VisualCors.browser("https://app.example.com", "https://api.example.com",
                Policy.allowAnyOrigin());

        // The moment the call carries cookies, the wildcard becomes illegal:
        // "any page may read this" cannot be true of a logged-in user's data.
        cors.send(Request.get("/me").withCredentials());

        // Naming the origin is still not enough -- allowing an origin to READ is
        // not the same as allowing it to act as the signed-in user.
        cors.reconfigure(Policy.allowOrigin("https://app.example.com"));
        cors.send(Request.get("/me").withCredentials());

        // Both halves together are what a credentialed request needs.
        cors.reconfigure(Policy.allowOrigin("https://app.example.com").allowCredentials());
        cors.send(Request.get("/me").withCredentials());

        cors.report();
        System.out.println("Cookies need an exact origin plus Access-Control-Allow-Credentials.");
    }
}
