import visual.VisualAuthentication;
import visual.VisualAuthentication.Credential;

public class Playground {
    public static void main(String[] args) {
        // With a server-side session, logging out is a DELETE, and it is
        // immediate: the next request finds nothing behind the id.
        VisualAuthentication stateful = VisualAuthentication.withSessions();
        stateful.register("alice", "correct-horse-battery");
        Credential cookie = stateful.login("alice", "correct-horse-battery");
        stateful.logout(cookie);
        stateful.requestAs("mallory", "/api/profile", cookie);

        // With a stateless token there is nothing to delete. The client drops
        // its copy; a copy made anywhere else keeps working until it expires.
        VisualAuthentication stateless = VisualAuthentication.withTokens();
        stateless.register("alice", "correct-horse-battery");
        Credential token = stateless.login("alice", "correct-horse-battery");
        stateless.logout(token);
        stateless.requestAs("mallory", "/api/profile", token);

        stateless.report();
        System.out.println("'Log out' is a server decision, so the server has to be able to make it.");
    }
}
