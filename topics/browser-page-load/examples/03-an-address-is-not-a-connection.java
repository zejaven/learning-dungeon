import visual.VisualPageLoad;

public class Playground {
    public static void main(String[] args) {
        VisualPageLoad browser = VisualPageLoad.browser();

        browser.type("https://www.google.com");
        browser.resolve();

        // SYN, SYN-ACK, ACK. One full round trip, and TCP never sees the host
        // name: the connection is to an address and a port.
        browser.connect();

        // Look at the audit: zero HTTP requests so far. Everything up to this
        // point was spent getting into a position to ask.
        browser.report();

        System.out.println("A connection is not a request. Nothing has been asked yet.");
    }
}
