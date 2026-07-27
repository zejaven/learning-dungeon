import visual.VisualJwt;
import visual.VisualJwt.Token;

public class Playground {
    public static void main(String[] args) {
        // One first-party browser app talking to one backend. Same story twice.
        // Read the two audit lines against each other at the end.
        VisualJwt spa = VisualJwt.jwt().service("api");
        Token bearer = spa.issue("alice", "admin", "api");
        spa.verifyAt("api", bearer);
        spa.verifyAt("api", bearer);
        spa.changeRole("alice", "reader");   // an admin demotes her mid-session
        spa.verifyAt("api", bearer);
        spa.logout(bearer);                  // she signs out on a shared laptop
        spa.verifyAt("api", bearer);
        spa.report();

        VisualJwt classic = VisualJwt.sessions().service("api");
        Token cookie = classic.issue("alice", "admin", "api");
        classic.verifyAt("api", cookie);
        classic.verifyAt("api", cookie);
        classic.changeRole("alice", "reader");
        classic.verifyAt("api", cookie);
        classic.logout(cookie);
        classic.verifyAt("api", cookie);
        classic.report();
    }
}
