import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;
import visual.VisualEndpointSecurity.Token;

public class Playground {
    public static void main(String[] args) {
        VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
        api.rule("/api/orders/**", Access.authenticated());

        // Four tokens that all look perfectly fine when you print them, and
        // that a library refuses for four different reasons.
        api.send(Request.get("/api/orders/42").bearer(Token.forUser("alice").forged()));
        api.send(Request.get("/api/orders/42")
                .bearer(Token.forUser("alice").fromIssuer("https://evil.example.com")));
        api.send(Request.get("/api/orders/42")
                .bearer(Token.forUser("alice").forAudience("billing-api")));
        api.send(Request.get("/api/orders/42").bearer(Token.forUser("alice").expired()));
        api.report();

        // The same forged token against an API that "just reads the user id
        // out of the JWT". Signature, issuer, audience and expiry are all
        // parsed and none of them is checked.
        VisualEndpointSecurity decodeOnly = VisualEndpointSecurity.denyByDefault();
        decodeOnly.trustUnverifiedTokens();
        decodeOnly.rule("/api/admin/**", Access.hasRole("ADMIN"));
        decodeOnly.send(Request.get("/api/admin/users")
                .bearer(Token.forUser("mallory").roles("ADMIN").forged()));
        decodeOnly.report();

        System.out.println("A token you do not verify is a claim the client typed.");
    }
}
