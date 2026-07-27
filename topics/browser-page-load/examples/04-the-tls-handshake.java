import visual.VisualPageLoad;

public class Playground {
    public static void main(String[] args) {
        // TLS 1.3: the client guesses the key exchange in its first message,
        // so the handshake fits into one round trip.
        VisualPageLoad modern = VisualPageLoad.browser().usingTls("TLS 1.3");
        modern.type("https://www.google.com");
        modern.resolve();
        modern.connect();
        modern.secure();
        modern.report();

        // The same page over TLS 1.2, which cannot start the key exchange until
        // the server has spoken. One extra round trip, every single connection.
        VisualPageLoad legacy = VisualPageLoad.browser().usingTls("TLS 1.2");
        legacy.type("https://www.google.com");
        legacy.resolve();
        legacy.connect();
        legacy.secure();
        legacy.report();

        System.out.println("TLS sits between TCP and HTTP and costs round trips, not bandwidth.");
    }
}
