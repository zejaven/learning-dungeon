import visual.VisualAuthentication;
import visual.VisualAuthentication.Credential;

public class Playground {
    public static void main(String[] args) {
        VisualAuthentication auth = VisualAuthentication.withSessions();

        // Enrolment. The system learns something it can check a password
        // against later, without ever keeping the password itself.
        auth.register("alice", "correct-horse-battery");

        // Nobody has proved anything yet, so the server does not know who this
        // is. Anonymous is not an error - it is simply "no identity".
        auth.request("/api/profile");

        // The one moment the password is used. What comes back is a credential.
        Credential session = auth.login("alice", "correct-horse-battery");

        // Every later request carries that credential, and every one of them is
        // checked again: the server remembers nothing between requests.
        auth.request("/api/profile", session);
        auth.request("/api/orders", session);

        auth.report();
        System.out.println("Authentication answers exactly one question: who is calling?");
    }
}
