import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        // Three services, one credential. With a JWT each of them needs only the
        // key: no shared database, no call to the auth service, nothing to be down.
        VisualJwt mesh = VisualJwt.jwt().service("orders").service("billing").service("search");
        Token bearer = mesh.issue("alice", "user", "orders");
        mesh.verifyAt("orders", bearer);
        mesh.verifyAt("billing", bearer);
        mesh.verifyAt("search", bearer);
        mesh.report();

        // The same three services on opaque session ids: every request is a
        // lookup, and every service now depends on one shared store.
        VisualJwt shared = VisualJwt.sessions().service("orders").service("billing").service("search");
        Token cookie = shared.issue("alice", "user", "orders");
        shared.verifyAt("orders", cookie);
        shared.verifyAt("billing", cookie);
        shared.verifyAt("search", cookie);
        shared.report();
    }
}
