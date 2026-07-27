import visual.VisualTls;

public class Playground {
    public static void main(String[] args) {
        // TLS 1.3: the session key comes from an ephemeral exchange that is
        // thrown away when the connection closes.
        VisualTls modern = VisualTls.browser();
        modern.connect("bank.example", VisualTls.certificateFor("bank.example"));
        modern.send("GET /statements HTTP/1.1");
        modern.eavesdropperRecords();
        modern.stealPrivateKeyLater();

        // The old TLS 1.2 alternative: the client encrypts the session secret
        // to the certificate's public key, tying every session to one key.
        VisualTls legacy = VisualTls.browser().useRsaKeyTransport();
        legacy.connect("bank.example", VisualTls.certificateFor("bank.example"));
        legacy.send("GET /statements HTTP/1.1");
        legacy.eavesdropperRecords();
        legacy.stealPrivateKeyLater();
        legacy.report();
    }
}
