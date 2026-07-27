import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;
import visual.VisualEndpointSecurity.Token;

public class Playground {
    public static void main(String[] args) {
        Token alice = Token.forUser("alice").roles("USER");

        // The rule everybody writes: "you must be logged in to read an order".
        // It is true, it passes review, and it protects nothing about WHICH
        // order you read.
        VisualEndpointSecurity endpointOnly = VisualEndpointSecurity.denyByDefault();
        endpointOnly.rule("/api/orders/**", Access.authenticated());
        endpointOnly.owner("/api/orders/42", "bob");
        endpointOnly.send(Request.get("/api/orders/42").bearer(alice));
        endpointOnly.report();

        // The same rule plus an object-level check: the record must belong to
        // the caller. Note the 404 -- a 403 would confirm that order 42 exists.
        VisualEndpointSecurity perObject = VisualEndpointSecurity.denyByDefault();
        perObject.rule("/api/orders/**", Access.authenticated().ownerOnly());
        perObject.owner("/api/orders/42", "bob");
        perObject.owner("/api/orders/7", "alice");
        perObject.send(Request.get("/api/orders/42").bearer(alice));
        perObject.send(Request.get("/api/orders/7").bearer(alice));
        perObject.report();

        System.out.println("Authenticated is not the same as entitled to THIS row.");
    }
}
