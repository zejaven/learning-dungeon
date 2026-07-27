import visual.VisualAuthentication;
import visual.VisualAuthentication.Credential;

public class Playground {
    public static void main(String[] args) {
        VisualAuthentication auth = VisualAuthentication.withSessions().rateLimitLogins(3);
        auth.register("alice", "correct-horse-battery");

        // A failed login hands out nothing at all, so the caller is still
        // anonymous - there is no half-authenticated state to leak.
        Credential nothing = auth.login("alice", "letmein");
        auth.request("/api/profile", nothing);

        // Guessing is cheap: every attempt is a perfectly valid request, and
        // only their number gives the attack away.
        auth.login("alice", "password1");
        auth.login("alice", "qwerty");

        // Note that the throttle now refuses even the CORRECT password. That is
        // the trade-off: locking an account also locks its owner out.
        auth.login("alice", "correct-horse-battery");

        auth.report();
        System.out.println("Never say WHICH of the two was wrong - that answer is an account list.");
    }
}
