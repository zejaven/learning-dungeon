import visual.VisualCrypto;
import visual.VisualCrypto.KeyPair;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();
        KeyPair bob = lab.generateKeyPair("Bob");
        lab.publish(bob);

        // RSA-2048 encrypts one number smaller than the modulus, so the key size
        // is also the message size: 256 bytes minus OAEP padding.
        lab.encryptBlobFor(bob, "quarterly-report.pdf", 1048576);

        // Splitting the file into blocks is possible. Look at what it would cost.
        lab.compareCost("quarterly-report.pdf", 1048576);
    }
}
