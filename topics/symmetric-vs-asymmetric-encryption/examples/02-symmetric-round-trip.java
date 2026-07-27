import visual.VisualCrypto;
import visual.VisualCrypto.Envelope;
import visual.VisualCrypto.SecretKey;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();
        SecretKey orders = lab.generateSecretKey("orders-key", "Alice", "Bob");

        // The same key in both directions - that symmetry is the definition.
        Envelope payment = lab.encrypt(orders, "transfer 100 to account 42");
        lab.decrypt(orders, payment);

        // Another perfectly good 256-bit key is not "almost right"; it is nothing.
        SecretKey billing = lab.generateSecretKey("billing-key", "Carol");
        lab.decrypt(billing, payment);
    }
}
