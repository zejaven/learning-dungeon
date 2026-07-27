import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        VisualJwt auth = VisualJwt.sessions().service("orders");
        Token cookie = auth.issue("alice", "user", "orders");

        // There is nothing inside it. Whoever steals it learns only that they
        // hold a session id - not who the user is or what they may do.
        auth.decode(cookie);
        auth.verifyAt("orders", cookie);

        // Revocation is a DELETE, and it lands on the very next request.
        auth.logout(cookie);
        auth.verifyAt("orders", cookie);
        auth.report();
    }
}
