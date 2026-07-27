import visual.VisualHttpResource;
import visual.VisualHttpResource.Body;

public class Playground {
    public static void main(String[] args) {
        // The server already holds a four-field representation at this URL.
        VisualHttpResource api = VisualHttpResource.serving("/users/7",
                Body.of("name", "Ada")
                        .and("email", "ada@example.com")
                        .and("role", "reader")
                        .and("phone", "+1-555-0100"));

        // PUT means "make this URL hold exactly this". The client wants to change
        // the name and the role, so it sends the WHOLE representation with those
        // two values edited and the other two carried over unchanged.
        api.put(Body.of("name", "Ada Lovelace")
                .and("email", "ada@example.com")
                .and("role", "admin")
                .and("phone", "+1-555-0100"));

        api.report();
        System.out.println("A complete body loses nothing: PUT replaced four fields with four fields.");
    }
}
