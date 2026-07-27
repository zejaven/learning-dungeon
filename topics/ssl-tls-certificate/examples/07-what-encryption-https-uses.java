import visual.VisualTls;

public class Playground {
    public static void main(String[] args) {
        VisualTls browser = VisualTls.browser();

        // Asymmetric crypto authenticates the server and agrees on a key.
        // Symmetric crypto - AES-256-GCM - carries every byte after that.
        browser.connect("shop.example", VisualTls.certificateFor("shop.example"));
        browser.send("GET /account HTTP/1.1");

        // GCM is an AEAD cipher: the same operation that hides the record also
        // authenticates it, so a flipped byte is detected, not decrypted.
        browser.tamperInFlight();

        // The same request with no TLS at all, for comparison.
        browser.sendWithoutTls("GET /account HTTP/1.1");
    }
}
