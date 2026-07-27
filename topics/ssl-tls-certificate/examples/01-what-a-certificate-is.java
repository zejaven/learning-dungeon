import visual.VisualTls;
import visual.VisualTls.Certificate;

public class Playground {
    public static void main(String[] args) {
        VisualTls browser = VisualTls.browser();

        // A certificate is a public document. The server hands the same bytes
        // to every visitor, so nothing inside it can be a secret.
        Certificate cert = VisualTls.certificateFor("shop.example", "www.shop.example");
        browser.inspect(cert);

        System.out.println("subject      : " + cert.subject());
        System.out.println("names (SAN)  : " + cert.names());
        System.out.println("issuer       : " + cert.issuer());
        System.out.println("valid        : day " + cert.validFrom() + " .. day " + cert.validUntil());
        System.out.println("public key   : " + cert.keyType() + " " + cert.publicKey());
        System.out.println("private key  : never leaves the server");

        // The same four fields, signed by nobody but itself. The document does
        // not get weaker - the signature at the bottom does.
        browser.inspect(VisualTls.selfSignedCertificateFor("shop.example"));
    }
}
