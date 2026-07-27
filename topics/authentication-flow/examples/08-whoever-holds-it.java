import visual.VisualAuthentication;
import visual.VisualAuthentication.Credential;

public class Playground {
    public static void main(String[] args) {
        VisualAuthentication auth = VisualAuthentication.withSessions();
        auth.register("alice", "correct-horse-battery");

        Credential cookie = auth.login("alice", "correct-horse-battery");
        auth.request("/api/profile", cookie);

        // Mallory did not break anything: they copied a value - off an open
        // network, out of a log, or with a script on the page - and sent it.
        auth.requestAs("mallory", "/api/profile", cookie);

        // Nothing detects this, so the only real limit is how long the value is
        // worth having.
        auth.advanceMinutes(20);
        auth.requestAs("mallory", "/api/profile", cookie);

        auth.report();
        System.out.println("A credential is a bearer credential: holding it IS being the user.");
    }
}
