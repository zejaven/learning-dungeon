import visual.VisualPageLoad;

public class Playground {
    public static void main(String[] args) {
        VisualPageLoad browser = VisualPageLoad.browser();

        browser.type("https://www.google.com/search?q=cats");
        browser.resolve();
        browser.connect();
        browser.secure();

        // Only now does the request you actually wanted exist on the wire.
        browser.request();

        // TTFB = one more round trip + however long the server thinks.
        browser.respond();

        // The document is not the page: the parser finds what else it needs,
        // and each of those is another request on the same connection.
        browser.parseHtml("/styles.css", "/app.js");
        browser.fetch("/styles.css");
        browser.fetch("/app.js");
        browser.render();
        browser.report();

        System.out.println("One navigation, three HTTP requests, one connection.");
    }
}
