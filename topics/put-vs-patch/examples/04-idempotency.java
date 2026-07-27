import visual.VisualHttpResource;
import visual.VisualHttpResource.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpResource api = VisualHttpResource.serving("/users/7",
                Body.of("name", "Ada")
                        .and("email", "ada@example.com")
                        .and("role", "reader")
                        .and("tags", "internal"));

        // A full PUT describes the state to end up in, so sending it twice --
        // a retry after a timeout, say -- lands on that same state.
        api.put(Body.of("name", "Ada").and("email", "ada@example.com")
                .and("role", "admin").and("tags", "internal"));
        api.put(Body.of("name", "Ada").and("email", "ada@example.com")
                .and("role", "admin").and("tags", "internal"));

        // A merge patch also describes a target value, so it repeats safely too.
        api.patch(Body.of("role", "owner"));
        api.patch(Body.of("role", "owner"));

        // This patch carries an OPERATION instead: add "beta" to the tags list.
        // Applying an operation twice performs it twice -- which is why HTTP
        // does not promise that PATCH is idempotent.
        api.patchAppend("tags", "beta");
        api.patchAppend("tags", "beta");

        api.report();
        System.out.println("Idempotency comes from the body's meaning, not from the method's name.");
    }
}
