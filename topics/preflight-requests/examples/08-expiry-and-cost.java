import visual.VisualPreflight;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

public class Playground {
    public static void main(String[] args) {
        String page = "https://app.example.com";

        // A cautious 60-second Max-Age.
        VisualPreflight browser = VisualPreflight.browser(page, "https://api.example.com",
                Api.cors().allowOrigin(page)
                        .allowMethods("POST")
                        .allowHeaders("Content-Type")
                        .maxAge(60));

        browser.send(Call.post("/orders").json());

        // The user reads the page for a while, and the permission goes stale.
        browser.advanceSeconds(90);
        browser.send(Call.post("/orders").json());

        // Asking for a day does not get you a day: browsers cap what they keep.
        browser.redeploy(Api.cors().allowOrigin(page)
                .allowMethods("POST")
                .allowHeaders("Content-Type")
                .maxAge(86400));
        browser.send(Call.post("/orders").json());
        browser.advanceSeconds(3600);
        browser.send(Call.post("/orders").json());

        browser.report();
        System.out.println("Count the round trips: that is the latency a preflight really costs.");
    }
}
