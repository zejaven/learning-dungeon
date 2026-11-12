import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        // The GET handler was reviewed and fixed. PATCH and DELETE were
        // written by someone else, and each loads the order by id alone.
        VisualAccessControl api = VisualAccessControl.naive();
        api.enforce(Defense.OWNER_ON_READ);
        Caller alice = api.user("alice");
        api.user("bob");
        api.order("8", "bob", 120);
        api.order("9", "bob", 45);

        api.get(alice, "8");                       // refused
        api.update(alice, "8", "note", "cancel");  // served
        api.delete(alice, "9");                    // served

        // Every handler that touches a record by id needs the check,
        // which is why it belongs in one place (a repository method or
        // a policy), not copied into each controller.
        api.enforce(Defense.OWNER_ON_WRITE);
        api.update(alice, "8", "note", "cancel");
        api.report();
    }
}
