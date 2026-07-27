import visual.VisualTls;
import visual.VisualTls.Certificate;

public class Playground {
    public static void main(String[] args) {
        VisualTls browser = VisualTls.browser();

        // The real server: it holds the private key that matches the
        // certificate's public key, and it can prove it.
        Certificate genuine = VisualTls.certificateFor("shop.example");
        browser.connect("shop.example", genuine);

        // An impostor downloads the very same certificate - byte for byte,
        // CA signature and all - and presents it. Content checks all pass.
        browser.connect("shop.example", VisualTls.copyOf(genuine));
        System.out.println("copied certificate holds the private key: "
                + VisualTls.copyOf(genuine).hasPrivateKey());
    }
}
