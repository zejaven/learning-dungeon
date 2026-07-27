import visual.VisualPreflight;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

public class Playground {
    public static void main(String[] args) {
        String page = "https://app.example.com";

        // The CORS configuration is perfect -- but a security filter runs before
        // the CORS handler and demands authentication on every request.
        VisualPreflight browser = VisualPreflight.browser(page, "https://api.example.com",
                Api.cors().allowOrigin(page)
                        .allowMethods("GET", "POST")
                        .allowHeaders("Content-Type", "Authorization")
                        .authFilterBeforeCors());

        // The preflight is unauthenticated by design, so it can never pass.
        browser.send(Call.post("/orders").json().bearer("t0ken"));

        // Letting OPTIONS through the filter chain fixes a "CORS error" that was
        // never about the CORS configuration at all.
        browser.redeploy(Api.cors().allowOrigin(page)
                .allowMethods("GET", "POST")
                .allowHeaders("Content-Type", "Authorization"));
        browser.send(Call.post("/orders").json().bearer("t0ken"));

        browser.report();
        System.out.println("A 401 on OPTIONS is an auth bug wearing a CORS error message.");
    }
}
