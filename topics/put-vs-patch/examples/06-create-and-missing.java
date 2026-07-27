import visual.VisualHttpResource;
import visual.VisualHttpResource.Body;

public class Playground {
    public static void main(String[] args) {
        // Nothing is stored at this URL yet.
        VisualHttpResource api = VisualHttpResource.emptyAt("/users/9");

        // A patch describes how to change an existing representation. There is
        // nothing here to merge into, so the server answers 404.
        api.patch(Body.of("role", "admin"));

        // A PUT describes the final state, so the server can just create it.
        api.put(Body.of("name", "Grace").and("email", "grace@example.com").and("role", "admin"));

        // And it stays idempotent across the create: the retry finds the resource
        // already in the requested state and changes nothing.
        api.put(Body.of("name", "Grace").and("email", "grace@example.com").and("role", "admin"));

        api.report();
        System.out.println("PUT can create at a client-chosen URL; PATCH needs something to patch.");
    }
}
