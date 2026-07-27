import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;
import visual.VisualEndpointSecurity.Token;

public class Playground {
    public static void main(String[] args) {
        Token alice = Token.forUser("alice").roles("USER");

        // "Everything under /api needs a login" reads like a safe first line.
        // It is also a rule that matches /api/admin/users.
        VisualEndpointSecurity wrongOrder = VisualEndpointSecurity.denyByDefault();
        wrongOrder.rule("/api/**", Access.authenticated());
        wrongOrder.rule("/api/admin/**", Access.hasRole("ADMIN"));
        wrongOrder.send(Request.get("/api/admin/users").bearer(alice));
        wrongOrder.report();

        // The same two rules, swapped. The specific pattern is now reachable,
        // and the broad one becomes the fallback it was meant to be.
        VisualEndpointSecurity rightOrder = VisualEndpointSecurity.denyByDefault();
        rightOrder.rule("/api/admin/**", Access.hasRole("ADMIN"));
        rightOrder.rule("/api/**", Access.authenticated());
        rightOrder.send(Request.get("/api/admin/users").bearer(alice));
        rightOrder.report();

        System.out.println("A rule list is read top to bottom: specific first, broad last.");
    }
}
