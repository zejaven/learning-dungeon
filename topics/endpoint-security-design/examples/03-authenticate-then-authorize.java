import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;
import visual.VisualEndpointSecurity.Token;

public class Playground {
    public static void main(String[] args) {
        VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
        api.rule("/api/health", Access.permitAll());
        api.rule("/api/orders/**", Access.authenticated());
        api.rule("/api/admin/**", Access.hasRole("ADMIN"));

        // A deliberately public endpoint: no credential is demanded, because a
        // rule says so out loud. "Public" is a decision, not an accident.
        api.send(Request.get("/api/health"));

        // Nobody is calling. The API cannot say yes or no to a caller it has
        // not identified yet -> 401, "come back with a credential".
        api.send(Request.get("/api/orders/42"));

        // Now it knows exactly who is calling, and the answer is still no
        // -> 403. Retrying with the same token will never change that.
        Token alice = Token.forUser("alice").roles("USER");
        api.send(Request.get("/api/admin/users").bearer(alice));

        // Same endpoint, a caller whose token says ADMIN.
        api.send(Request.get("/api/admin/users").bearer(Token.forUser("root").roles("ADMIN")));

        api.report();
        System.out.println("401 = who are you? 403 = I know, and no.");
    }
}
