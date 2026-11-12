import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        VisualAccessControl api = VisualAccessControl.naive();
        api.enforce(Defense.OWNER_ON_READ);
        Caller alice = api.user("alice");
        api.user("bob");
        api.order("8", "bob", 120);

        // Both refused -- but the codes differ, so alice learns
        // that 8 exists and 9 does not. That is enumeration.
        api.get(alice, "8");
        api.get(alice, "9");

        // Querying WHERE id = ? AND owner = ? answers "not found" for both.
        api.enforce(Defense.NOT_FOUND_FOR_FOREIGN);
        api.get(alice, "8");
        api.get(alice, "9");
        api.report();
    }
}
