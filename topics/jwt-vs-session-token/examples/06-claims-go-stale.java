import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        // A token is a snapshot of facts that were true when it was signed.
        VisualJwt auth = VisualJwt.jwt().service("orders");
        Token token = auth.issue("alice", "admin", "orders");
        auth.changeRole("alice", "reader");
        auth.verifyAt("orders", token);

        // Even switching the account off changes nothing the verifier can see.
        auth.deactivate("alice");
        auth.verifyAt("orders", token);
        auth.report();

        // A session id carries no claims at all, so there is nothing to go stale.
        VisualJwt fresh = VisualJwt.sessions().service("orders");
        Token cookie = fresh.issue("alice", "admin", "orders");
        fresh.changeRole("alice", "reader");
        fresh.verifyAt("orders", cookie);
        fresh.deactivate("alice");
        fresh.verifyAt("orders", cookie);
        fresh.report();
    }
}
