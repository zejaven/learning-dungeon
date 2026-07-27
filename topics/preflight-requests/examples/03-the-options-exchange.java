import visual.VisualPreflight;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

public class Playground {
    public static void main(String[] args) {
        VisualPreflight browser = VisualPreflight.browser(
                "https://app.example.com", "https://api.example.com",
                Api.cors().allowOrigin("https://app.example.com")
                        .allowMethods("GET", "PUT", "DELETE")
                        .allowHeaders("Content-Type", "Authorization"));

        // One call, two requests. Follow the headers in both directions:
        //   browser -> Origin, Access-Control-Request-Method,
        //              Access-Control-Request-Headers (names only, no values)
        //   server  -> 204, Access-Control-Allow-Origin / -Methods / -Headers
        //
        // The OPTIONS request carries no body, no cookies and no Authorization
        // header -- the token below travels only on the second request.
        browser.send(Call.put("/orders/42").json().bearer("t0ken"));

        browser.report();
        System.out.println("Permission to SEND is negotiated first; the payload follows separately.");
    }
}
