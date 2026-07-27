import visual.VisualPageLoad;

public class Playground {
    public static void main(String[] args) {
        // One browser, two navigations. It keeps its DNS cache, its open
        // connection and its HTTP cache in between - that is the whole point.
        VisualPageLoad browser = VisualPageLoad.browser();

        browser.open("www.google.com");
        browser.report();

        // Same page, cold caches gone: the name is cached, the connection is
        // still open and every subresource is already on disk.
        browser.open("www.google.com");
        browser.report();

        System.out.println("The first request to an origin pays for all the ones after it.");
    }
}
