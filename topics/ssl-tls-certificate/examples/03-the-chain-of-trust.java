import visual.VisualTls;

public class Playground {
    public static void main(String[] args) {
        VisualTls browser = VisualTls.browser();

        // Leaf -> intermediate -> root. The root is not sent by the server:
        // it was already in the trust store, which is where trust comes from.
        browser.connect("shop.example", VisualTls.certificateFor("shop.example"));

        // Same leaf, same signatures - but the intermediate is missing, so
        // there is no path to walk. The famous "works in my browser" bug.
        browser.connect("shop.example", VisualTls.chainWithoutIntermediate("shop.example"));

        // A self-signed certificate is its own root, and nobody trusts it.
        browser.connect("shop.example", VisualTls.selfSignedCertificateFor("shop.example"));
    }
}
