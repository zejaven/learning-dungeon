import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;
import visual.VisualEndpointSecurity.Token;

public class Playground {
    public static void main(String[] args) {
        VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
        // Reads and writes are different endpoints even on the same path, so
        // they get different rules. The specific method rules come first.
        api.rule("POST", "/api/orders/**", Access.authenticated().scope("orders:write"));
        api.rule("GET", "/api/orders/**", Access.authenticated().scope("orders:read"));

        // Alice is a normal, fully entitled user -- but this token was issued
        // to a reporting integration she connected, and she only consented to
        // reading. The role says USER; the scope says read.
        Token reportingApp = Token.forUser("alice").roles("USER").scopes("orders:read");

        api.send(Request.get("/api/orders/42").bearer(reportingApp));
        api.send(Request.post("/api/orders").bearer(reportingApp));

        // Her own session token carries both scopes, so the write goes through.
        Token herOwnSession = Token.forUser("alice").roles("USER")
                .scopes("orders:read", "orders:write");
        api.send(Request.post("/api/orders").bearer(herOwnSession));

        api.report();
        System.out.println("Roles: what the user may do. Scopes: what this token may do.");
    }
}
