import visual.VisualTls;
import visual.VisualTls.Certificate;

public class Playground {
    public static void main(String[] args) {
        VisualTls browser = VisualTls.browser();

        // The cafe Wi-Fi answers instead of the shop, with a certificate for
        // shop.example that its own CA signed. Cryptographically flawless.
        Certificate rogue = VisualTls.certificateFromRoot("shop.example", "Cafe Wi-Fi CA");
        browser.connect("shop.example", rogue);

        // "Install our certificate to use this network." One click, and the
        // same rogue chain now validates - this is also how corporate TLS
        // inspection and every debugging proxy work.
        browser.installRoot("Cafe Wi-Fi CA");
        browser.connect("shop.example", rogue);
        browser.send("POST /login password=hunter2");
        browser.report();
    }
}
