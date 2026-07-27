import visual.VisualTls;

public class Playground {
    public static void main(String[] args) {
        VisualTls browser = VisualTls.browser();

        // Everything that turns a certificate into a secure connection happens
        // here, before a single byte of HTTP is sent.
        browser.connect("shop.example", VisualTls.certificateFor("shop.example"));

        // Only now does the request go out - and it goes out encrypted.
        browser.send("GET /orders/42 HTTP/1.1");
        browser.send("Cookie: session=7f21");
        browser.report();
    }
}
