import visual.VisualCors;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

public class Playground {
    public static void main(String[] args) {
        // An origin is the triple scheme + host + port. Two URLs are the same
        // origin only when all three match, and then the same-origin policy has
        // nothing to restrict: no Origin header, no permission, no CORS.
        VisualCors sameOrigin = VisualCors.browser(
                "https://app.example.com", "https://app.example.com", Policy.none());
        sameOrigin.send(Request.get("/api/orders"));
        sameOrigin.report();

        // Same scheme, same host, different port -- a different origin. The
        // server here is the very same machine, and it still needs CORS headers.
        VisualCors otherPort = VisualCors.browser(
                "https://app.example.com", "https://app.example.com:8443", Policy.none());
        otherPort.send(Request.get("/api/orders"));
        otherPort.report();

        System.out.println("Same host is not the same origin: the port is part of it.");
    }
}
