import visual.VisualPreflight;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

public class Playground {
    public static void main(String[] args) {
        String page = "https://app.example.com";

        // Access-Control-Allow-Headers: * reads as "any header is fine".
        VisualPreflight browser = VisualPreflight.browser(page, "https://api.example.com",
                Api.cors().allowOrigin(page).allowMethods("GET").allowAnyHeader());

        // It is not. The wildcard deliberately excludes Authorization.
        browser.send(Call.get("/me").bearer("t0ken"));

        // Naming that one header explicitly is the whole fix.
        browser.redeploy(Api.cors().allowOrigin(page)
                .allowMethods("GET")
                .allowHeaders("Authorization"));
        browser.send(Call.get("/me").bearer("t0ken"));

        // Cookies switch wildcards off entirely: for a credentialed call, * is
        // compared literally, so it matches a method or header actually called "*".
        browser.redeploy(Api.cors().allowOrigin(page).allowCredentials()
                .allowMethods("*")
                .allowAnyHeader());
        browser.send(Call.patch("/me").json().withCredentials());

        browser.report();
        System.out.println("A wildcard is not a blanket permission -- it has two written-down holes.");
    }
}
