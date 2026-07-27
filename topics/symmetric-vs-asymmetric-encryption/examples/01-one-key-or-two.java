import visual.VisualCrypto;
import visual.VisualCrypto.KeyPair;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();

        // Symmetric: ONE key, and a copy for everyone who must read.
        // It can never be published - reading and writing are the same power.
        lab.generateSecretKey("orders-key", "Alice", "Bob");

        // Asymmetric: TWO mathematically linked keys.
        KeyPair bob = lab.generateKeyPair("Bob");

        // One half is meant to be handed to strangers. That is not a leak.
        lab.publish(bob);
    }
}
