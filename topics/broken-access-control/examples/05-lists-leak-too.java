import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        VisualAccessControl api = VisualAccessControl.naive();
        Caller alice = api.user("alice");
        api.user("bob");
        api.order("7", "alice", 30);
        api.order("8", "bob", 120);
        api.order("9", "bob", 45);

        // The frontend always sends ?ownerId=<me>. The server trusts it.
        api.listByOwner(alice, "alice");   // looks fine
        api.listByOwner(alice, "bob");     // so does this, to the server
        api.list(alice);                   // and no filter means everything

        // The owner of a list comes from the session, never from a parameter.
        api.enforce(Defense.SCOPED_LISTS);
        api.listByOwner(alice, "bob");
        api.report();
    }
}
