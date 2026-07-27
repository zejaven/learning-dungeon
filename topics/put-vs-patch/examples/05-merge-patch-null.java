import visual.VisualHttpResource;
import visual.VisualHttpResource.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpResource api = VisualHttpResource.serving("/users/7",
                Body.of("name", "Ada")
                        .and("email", "ada@example.com")
                        .and("role", "reader")
                        .and("phone", "+1-555-0100"));

        // JSON Merge Patch has exactly one way to say "remove this member": send
        // it with an explicit null. The member is deleted, not set to null.
        api.patch(Body.of("phone", null));

        // Which is the catch: there is no merge patch body that stores a null
        // value. This one deletes the member as well, however you meant it.
        api.patch(Body.of("role", null));

        api.report();
        System.out.println("Deleting and clearing look identical in merge patch -- JSON Patch names the operation.");
    }
}
