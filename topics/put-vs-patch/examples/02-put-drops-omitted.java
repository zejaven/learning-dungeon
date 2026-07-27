import visual.VisualHttpResource;
import visual.VisualHttpResource.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpResource api = VisualHttpResource.serving("/users/7",
                Body.of("name", "Ada")
                        .and("email", "ada@example.com")
                        .and("role", "reader")
                        .and("phone", "+1-555-0100"));

        // The client only wants to change the role, so it sends only the role --
        // but it sends it with PUT. "Make this URL hold exactly this" is taken
        // literally: everything the body did not mention is not part of the
        // resource any more.
        api.put(Body.of("role", "admin"));

        api.report();
        System.out.println("Three fields deleted, status 200 OK: a partial PUT is a delete in disguise.");
    }
}
