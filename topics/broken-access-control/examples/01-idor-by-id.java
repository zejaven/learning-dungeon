import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        // Every endpoint requires a login. That answers WHO is calling,
        // not WHICH order they may read.
        VisualAccessControl api = VisualAccessControl.naive();
        Caller alice = api.user("alice");
        api.user("bob");
        api.order("7", "alice", 30);
        api.order("8", "bob", 120);

        api.get(alice, "7");   // her own order: fine
        api.get(alice, "8");   // +1 in the URL: bob's order, served

        // The fix is one condition in the query:
        // findByIdAndOwner(id, currentUser) instead of findById(id).
        api.enforce(Defense.OWNER_ON_READ);
        api.get(alice, "8");
        api.report();
    }
}
