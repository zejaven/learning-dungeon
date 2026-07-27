import visual.VisualCrypto;
import visual.VisualCrypto.Envelope;
import visual.VisualCrypto.KeyPair;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();
        KeyPair bob = lab.generateKeyPair("Bob");
        lab.publish(bob);

        // No shared secret was ever agreed and no key was ever sent.
        Envelope secret = lab.encryptFor(bob, "my password is hunter2");

        // Mallory has the public key too - it closes envelopes, it does not open them.
        lab.attackerTries(secret);

        // Only the half that never moved can undo the operation.
        lab.decryptWith(bob, secret);
    }
}
