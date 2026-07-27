import visual.VisualHttpResource;
import visual.VisualHttpResource.Body;

public class Playground {
    public static void main(String[] args) {
        VisualHttpResource api = VisualHttpResource.serving("/users/7",
                Body.of("name", "Ada")
                        .and("email", "ada@example.com")
                        .and("role", "reader")
                        .and("phone", "+1-555-0100"));

        // Editor B opens the form and holds a copy of version v1.
        String copyReadByB = api.etag();

        // Editor A changes one member in the meantime.
        api.patch(Body.of("role", "owner"));

        // Editor B saves the form, which sends the whole representation back --
        // including the role as it looked in B's stale copy. Unconditionally,
        // this silently reverts A's change: the classic lost update.
        api.put(Body.of("name", "Ada")
                .and("email", "ada@example.com")
                .and("role", "reader")
                .and("phone", "+1-555-0199"));

        // The same save with a precondition. The resource has moved on since v1,
        // so the server refuses the write instead of losing an update.
        api.putIfMatch(copyReadByB, Body.of("name", "Ada")
                .and("email", "ada@example.com")
                .and("role", "reader")
                .and("phone", "+1-555-0199"));

        api.report();
        System.out.println("A full-body write needs If-Match to be safe; PATCH narrows the window, not the rule.");
    }
}
