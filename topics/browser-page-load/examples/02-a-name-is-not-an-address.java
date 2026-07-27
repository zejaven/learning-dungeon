import visual.VisualPageLoad;

public class Playground {
    public static void main(String[] args) {
        VisualPageLoad browser = VisualPageLoad.browser();

        // Nothing has been sent yet: the browser is still turning the typed
        // string into a URL with a scheme, a port and a path.
        browser.type("www.google.com");

        // A cold lookup: three local caches miss, then the recursive resolver
        // walks root -> .com -> the authoritative servers for google.com.
        browser.resolve();
        browser.report();

        // Same name a moment later. The answer is cached for its TTL, so this
        // whole layer disappears.
        browser.type("www.google.com/images");
        browser.resolve();
        browser.report();

        System.out.println("DNS returns an address. It does not open anything.");
    }
}
