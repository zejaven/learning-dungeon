import visual.VisualCrypto;
import visual.VisualCrypto.Envelope;
import visual.VisualCrypto.KeyPair;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();
        KeyPair bob = lab.generateKeyPair("Bob");
        lab.publish(bob);

        // The same file asymmetric encryption refused - now with a fresh session
        // key wrapped by the public key and the data carried by AES.
        Envelope envelope = lab.sendHybridBlob(bob, "quarterly-report.pdf", 1048576);
        lab.attackerTries(envelope);
        lab.openHybrid(bob, envelope);

        // Count the operations: two asymmetric, two symmetric, any payload size.
        lab.report();
    }
}
