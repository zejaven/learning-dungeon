import visual.VisualCrypto;
import visual.VisualCrypto.Envelope;
import visual.VisualCrypto.KeyPair;

public class Playground {
    public static void main(String[] args) {
        VisualCrypto lab = VisualCrypto.lab();
        KeyPair bob = lab.generateKeyPair("Bob");

        // Giving one half of the pair to the whole world costs nothing.
        lab.publish(bob);
        Envelope minutes = lab.sendHybrid(bob, "board minutes: Q3 write-down");
        lab.attackerTries(minutes);

        // Losing the other half costs everything ever addressed to it - including
        // traffic somebody recorded long before the leak.
        lab.leakPrivateKey(bob);
        lab.attackerTries(minutes);
        lab.report();
    }
}
