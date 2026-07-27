import visual.VisualCrypto;
import visual.VisualCrypto.Envelope;
import visual.VisualCrypto.SecretKey;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();
        SecretKey orders = lab.generateSecretKey("orders-key", "Alice");
        Envelope payment = lab.encrypt(orders, "transfer 100 to account 42");

        // Bob cannot read anything until he has the key - so the key travels
        // over the very channel the key was supposed to protect.
        lab.shareKeyInTheOpen(orders, "Bob");
        lab.attackerTries(payment);

        // The second half of the problem is arithmetic: a key per pair.
        lab.keyDistributionCost(2);
        lab.keyDistributionCost(50);
    }
}
