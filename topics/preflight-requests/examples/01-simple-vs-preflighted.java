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

        // A safelisted method with no extra headers: nothing new is being asked
        // for, so there is nothing to ask permission about.
        browser.send(Call.get("/orders"));

        // Exactly what an HTML <form method="post"> sends. Forms could post
        // cross-origin long before fetch() existed, so this stays simple too.
        browser.send(Call.post("/orders").formEncoded());

        // The same POST with a JSON body. One header value moves it off the
        // safelist -- and one call becomes two round trips.
        browser.send(Call.post("/orders").json());

        browser.report();
        System.out.println("The line is not GET vs POST: it is what a form could already have sent.");
    }
}
