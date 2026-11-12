import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        // Ownership on writes is checked -- for the id in the PATH.
        VisualAccessControl api = VisualAccessControl.naive();
        api.enforce(Defense.OWNER_ON_WRITE);
        Caller alice = api.user("alice");
        api.user("bob");
        api.order("7", "alice", 30);
        api.order("8", "bob", 120);

        // Fields the client must never set: price and status.
        api.update(alice, "7", "note", "gift wrap", "price", "1", "status", "PAID");
        // And the id: the check ran on 7, the entity is saved as 8.
        api.update(alice, "7", "id", "8", "note", "cancel this");

        // Bind into a DTO that only has `note`; the path id is the only id.
        api.enforce(Defense.FIELD_ALLOWLIST);
        api.update(alice, "7", "id", "8", "price", "1", "note", "gift wrap");
        api.report();
    }
}
