import visual.VisualCrypto;
import visual.VisualCrypto.Envelope;
import visual.VisualCrypto.KeyPair;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();
        KeyPair alice = lab.generateKeyPair("Alice");
        lab.publish(alice);

        // Private key to produce, public key to check: authenticity, not secrecy.
        Envelope invoice = lab.sign(alice, "invoice 42: pay 100 EUR to Alice");
        lab.verify(alice, invoice);

        // A signed message is not a secret message.
        lab.attackerTries(invoice);

        // But it cannot be rewritten by anyone without the private key.
        lab.tamper(invoice);
        lab.verify(alice, invoice);
    }
}
