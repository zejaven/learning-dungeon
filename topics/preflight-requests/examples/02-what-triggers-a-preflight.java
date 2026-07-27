import visual.VisualPreflight;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

public class Playground {
    public static void main(String[] args) {
        VisualPreflight browser = VisualPreflight.browser(
                "https://app.example.com", "https://api.example.com",
                Api.cors().allowOrigin("https://app.example.com")
                        .allowMethods("GET", "POST", "PATCH")
                        .allowHeaders("Content-Type", "X-Request-Id"));

        // Trigger 1: the method. PUT, PATCH and DELETE are never simple.
        browser.send(Call.patch("/orders/42").formEncoded());

        // Trigger 2: a header outside the safelist -- even on a plain GET that
        // only reads. Tracing and correlation headers cost a round trip.
        browser.send(Call.get("/orders").header("X-Request-Id", "r-1"));

        // Trigger 3: the Content-Type VALUE. Only urlencoded, multipart and
        // text/plain are safelisted; application/json is not.
        browser.send(Call.post("/orders").json());

        // Not a trigger: Accept is on the safelist, so this stays simple.
        browser.send(Call.get("/orders").header("Accept", "application/json"));

        browser.report();
        System.out.println("Method, header name, or Content-Type value -- any one of them is enough.");
    }
}
