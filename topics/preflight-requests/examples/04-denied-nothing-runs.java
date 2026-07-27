import visual.VisualPreflight;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

public class Playground {
    public static void main(String[] args) {
        VisualPreflight browser = VisualPreflight.browser(
                "https://app.example.com", "https://api.example.com",
                Api.cors().allowOrigin("https://app.example.com")
                        .allowMethods("GET", "POST")
                        .allowHeaders("Content-Type"));

        // DELETE is missing from Access-Control-Allow-Methods, so the answer to
        // OPTIONS is a no. The real DELETE is never sent: order 42 still exists.
        browser.send(Call.delete("/orders/42"));

        // A listed method with an unlisted header fails the same way.
        browser.send(Call.post("/orders").json().header("X-Tenant", "acme"));

        browser.report();
        System.out.println("A denied preflight is the one CORS failure that reaches no handler.");
    }
}
