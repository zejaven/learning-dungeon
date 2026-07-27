import visual.VisualPreflight;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

public class Playground {
    public static void main(String[] args) {
        VisualPreflight browser = VisualPreflight.browser(
                "https://app.example.com", "https://api.example.com",
                Api.cors().allowOrigin("https://app.example.com")
                        .allowMethods("GET", "POST", "PUT")
                        .allowHeaders("Content-Type")
                        .maxAge(600));

        // The first JSON POST pays for the handshake.
        browser.send(Call.post("/orders").json());

        // An identical call reuses the answer: one round trip, not two.
        browser.send(Call.post("/orders").json());

        // The cache key is path + method + header set + credentials mode, so a
        // different method against the same path is a different entry.
        browser.send(Call.put("/orders/42").json());

        // ...and so is the same method against a different path.
        browser.send(Call.post("/invoices").json());

        browser.report();
        System.out.println("Max-Age turns a per-call cost into a per-combination cost.");
    }
}
