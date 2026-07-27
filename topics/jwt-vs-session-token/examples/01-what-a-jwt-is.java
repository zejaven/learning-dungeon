import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        VisualJwt auth = VisualJwt.jwt().service("orders");

        // A login has already happened; this is the credential handed back.
        Token token = auth.issue("alice", "user", "orders");

        // Nobody needs a key to read it - base64url is an encoding, not a cipher.
        auth.decode(token);
        System.out.println("on the wire: " + token.value());
        System.out.println("size: " + token.size() + " bytes, on every single request");

        // The service checks the signature it can recompute, and nothing else.
        auth.verifyAt("orders", token);
        auth.report();
    }
}
