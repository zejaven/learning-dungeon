import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        // Press "log out" on a stateless token and watch what the server does.
        VisualJwt auth = VisualJwt.jwt().service("orders");
        Token token = auth.issue("alice", "user", "orders");
        auth.logout(token);
        auth.verifyAt("orders", token);

        // Only time can end it on its own.
        auth.advanceMinutes(VisualJwt.ACCESS_TTL_MINUTES + 1);
        auth.verifyAt("orders", token);
        auth.report();

        // A deny-list makes logout real again - by putting the state back.
        VisualJwt revocable = VisualJwt.jwt().service("orders").denyList();
        Token revoked = revocable.issue("alice", "user", "orders");
        revocable.logout(revoked);
        revocable.verifyAt("orders", revoked);
        revocable.report();
    }
}
