import visual.VisualAuthentication;
import visual.VisualAuthentication.Credential;

public class Playground {
    public static void main(String[] args) {
        // Style 1: the server remembers. The client holds a meaningless id and
        // every request costs one lookup - watch the server store fill up.
        VisualAuthentication stateful = VisualAuthentication.withSessions();
        stateful.register("alice", "correct-horse-battery");
        Credential cookie = stateful.login("alice", "correct-horse-battery");
        stateful.request("/api/profile", cookie);

        // Style 2: the client holds the facts, signed. The server store stays
        // empty, so any instance can serve the request with no lookup at all.
        VisualAuthentication stateless = VisualAuthentication.withTokens();
        stateless.register("alice", "correct-horse-battery");
        Credential token = stateless.login("alice", "correct-horse-battery");
        stateless.request("/api/profile", token);

        System.out.println("Same five phases; the only difference is WHERE the truth lives.");
    }
}
