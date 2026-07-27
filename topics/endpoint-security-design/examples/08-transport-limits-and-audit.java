import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;
import visual.VisualEndpointSecurity.Token;

public class Playground {
    public static void main(String[] args) {
        VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
        api.rule("POST", "/api/login", Access.permitAll());
        api.rule("/api/orders/**", Access.authenticated());
        api.rateLimit(3);

        Token alice = Token.forUser("alice").roles("USER");

        // Gate zero. The request is refused -- and the token was still readable
        // by every hop on the way, so it now has to be rotated.
        api.send(Request.get("/api/orders/42").bearer(alice).overPlainHttp());

        // The same call over TLS behaves normally.
        api.send(Request.get("/api/orders/42").bearer(alice));

        // /api/login is public by design, which is exactly why it needs a
        // limit: every one of these attempts is a valid request on its own.
        for (int attempt = 1; attempt <= 4; attempt++) {
            api.send(Request.post("/api/login"));
        }

        api.report();
        System.out.println("Transport, limits and audit protect what auth cannot see.");
    }
}
