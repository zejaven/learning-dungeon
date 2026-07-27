import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;

public class Playground {
    public static void main(String[] args) {
        // Team A protects everything it could think of: the admin pages need a
        // role, and everything else is "obviously internal anyway".
        VisualEndpointSecurity permissive = VisualEndpointSecurity.permitByDefault();
        permissive.rule("/api/admin/**", Access.hasRole("ADMIN"));

        // A sprint later somebody adds a metrics endpoint. Nobody touched the
        // rule list -- nobody had to, because the API serves what it cannot
        // find a rule for.
        permissive.send(Request.get("/api/internal/metrics"));
        permissive.report();

        // Team B starts from the other end: nothing is reachable until a rule
        // opens it. Exactly the same forgotten endpoint now fails closed.
        VisualEndpointSecurity strict = VisualEndpointSecurity.denyByDefault();
        strict.rule("/api/admin/**", Access.hasRole("ADMIN"));
        strict.send(Request.get("/api/internal/metrics"));
        strict.report();

        System.out.println("Same code, same omission. Only the default stance differs.");
    }
}
