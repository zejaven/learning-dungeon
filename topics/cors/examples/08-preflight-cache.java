import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        // No Access-Control-Max-Age: every JSON call pays for two round trips.
        VisualCors uncached = VisualCors.browser("https://app.example.com", "https://api.example.com",
                Policy.allowOrigin("https://app.example.com")
                        .allowMethods("POST").allowHeaders("Content-Type"));
        uncached.send(Request.post("/orders").json());
        uncached.send(Request.post("/orders").json());
        uncached.report();

        // The same policy plus Access-Control-Max-Age: the browser remembers the
        // answer for this method + header combination and stops asking.
        VisualCors cached = VisualCors.browser("https://app.example.com", "https://api.example.com",
                Policy.allowOrigin("https://app.example.com")
                        .allowMethods("POST").allowHeaders("Content-Type").maxAge(600));
        cached.send(Request.post("/orders").json());
        cached.send(Request.post("/orders").json());
        cached.send(Request.post("/orders").json());
        cached.report();

        System.out.println("Three calls, one preflight: Access-Control-Max-Age is the cheap win.");
    }
}
