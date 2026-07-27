import visual.VisualAuthentication;
import visual.VisualAuthentication.Credential;

public class Playground {
    public static void main(String[] args) {
        VisualAuthentication auth = VisualAuthentication.withTokens();
        auth.register("alice", "correct-horse-battery");

        Credential token = auth.login("alice", "correct-horse-battery");
        auth.request("/api/profile", token);

        // Nobody called anything; only the clock moved. That is enough.
        auth.advanceMinutes(20);
        auth.request("/api/profile", token);

        // A short-lived credential does not mean logging in every 15 minutes:
        // the login itself lives much longer and can mint a fresh one.
        Credential fresh = auth.refresh(token);
        auth.request("/api/profile", fresh);

        auth.report();
        System.out.println("Short access lifetime, long login lifetime - both goals at once.");
    }
}
