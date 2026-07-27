import visual.VisualTls;
import visual.VisualTls.Certificate;

public class Playground {
    public static void main(String[] args) {
        VisualTls browser = VisualTls.browser();

        // A perfectly valid certificate - for somebody else's name.
        browser.connect("shop.example", VisualTls.certificateFor("other.example"));

        // A wildcard covers exactly one label: api.shop.example matches,
        // eu.api.shop.example does not.
        Certificate wildcard = VisualTls.certificateFor("*.shop.example");
        browser.connect("api.shop.example", wildcard);
        browser.connect("eu.api.shop.example", wildcard);

        // Inside its dates, and withdrawn by the CA anyway - normally because
        // the private key leaked.
        browser.connect("shop.example", VisualTls.revokedCertificateFor("shop.example"));

        // Nothing was deployed and no code changed; the calendar simply moved
        // past notAfter, and the same certificate now takes the site down.
        Certificate cert = VisualTls.certificateFor("shop.example");
        browser.connect("shop.example", cert);
        browser.advanceDays(60);
        browser.connect("shop.example", cert);
    }
}
