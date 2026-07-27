import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        // Server to server: no browser, no cookie, no user pressing "log out".
        // Short-lived tokens each addressed to exactly one recipient.
        VisualJwt mesh = VisualJwt.jwt().service("orders").service("billing").checkAudience();

        Token forOrders = mesh.issue("alice", "user", "orders");
        mesh.verifyAt("orders", forOrders);

        // orders now calls billing. Replaying the caller's token does not work:
        // a valid signature means "auth issued this", never "issued for you".
        mesh.verifyAt("billing", forOrders);

        Token forBilling = mesh.issue("alice", "user", "billing");
        mesh.verifyAt("billing", forBilling);
        mesh.report();
    }
}
