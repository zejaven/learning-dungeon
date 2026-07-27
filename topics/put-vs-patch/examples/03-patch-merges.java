import visual.VisualHttpResource;
import visual.VisualHttpResource.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpResource api = VisualHttpResource.serving("/users/7",
                Body.of("name", "Ada")
                        .and("email", "ada@example.com")
                        .and("role", "reader")
                        .and("phone", "+1-555-0100"));

        // The same intent as the previous example, expressed with the method that
        // actually means it: PATCH carries only the change, and the server merges
        // it into what is already stored.
        api.patch(Body.of("role", "admin"));

        // A patch can add a member the resource never had, too.
        api.patch(Body.of("team", "analytics"));

        api.report();
        System.out.println("Only the named members moved; the client never had to know the rest.");
    }
}
