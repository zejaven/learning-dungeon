import visual.VisualAuthentication;
import visual.VisualAuthentication.Credential;

public class Playground {
    public static void main(String[] args) {
        VisualAuthentication auth = VisualAuthentication.withSessions();
        auth.register("alice", "correct-horse-battery");

        // Something you know (the password) plus something you have (the code).
        auth.requireSecondFactor("alice", "314159");

        // The password is right and alice is still not logged in.
        Credential half = auth.login("alice", "correct-horse-battery");

        // A half-finished login must not be usable, or the second factor would
        // be a suggestion rather than a requirement.
        auth.request("/api/profile", half);

        // This is where an attacker with a stolen password stops.
        auth.submitSecondFactor(half, "000000");

        Credential session = auth.submitSecondFactor(half, "314159");
        auth.request("/api/profile", session);

        auth.report();
        System.out.println("Two factors only help when they are two different KINDS of proof.");
    }
}
