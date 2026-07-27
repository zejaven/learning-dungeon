import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        VisualJwt auth = VisualJwt.jwt().service("orders");
        Token token = auth.issue("alice", "user", "orders");

        // Editing the payload is trivial. Producing a signature for it is not.
        auth.verifyAt("orders", auth.tamper(token, "role", "admin"));

        // "alg" is a field inside the value being checked. A correct verifier
        // pins the algorithm itself and refuses anything else.
        auth.verifyAt("orders", auth.stripSignature(token));

        // The same forgery against a verifier that believes the header.
        VisualJwt naive = VisualJwt.jwt().service("orders").trustAlgorithmHeader();
        Token issued = naive.issue("alice", "user", "orders");
        naive.verifyAt("orders", naive.stripSignature(issued));
        naive.report();
    }
}
