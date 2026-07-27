import visual.VisualEndpointSecurity;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;

public class Playground {
    public static void main(String[] args) {
        // Anyone can type any header. Curl does it in one flag.
        Request handWritten = Request.get("/api/admin/users")
                .header("X-User-Id", "mallory")
                .header("X-User-Role", "ADMIN");

        // A service that derives identity from a verified token ignores the
        // headers completely -- and still has no idea who is calling.
        VisualEndpointSecurity verified = VisualEndpointSecurity.denyByDefault();
        verified.rule("/api/admin/**", Access.hasRole("ADMIN"));
        verified.send(handWritten);
        verified.report();

        // A service that was written to sit behind a gateway, and then became
        // reachable directly: same request, admin access.
        VisualEndpointSecurity behindGateway = VisualEndpointSecurity.denyByDefault();
        behindGateway.trustIdentityHeaders();
        behindGateway.rule("/api/admin/**", Access.hasRole("ADMIN"));
        behindGateway.send(handWritten);
        behindGateway.report();

        System.out.println("Identity must come from something the caller cannot write.");
    }
}
