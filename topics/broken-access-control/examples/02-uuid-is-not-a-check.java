import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        // Random ids make guessing hard. They do not make a leaked id useless:
        // ids travel in links, logs, Referer headers, screenshots and emails.
        VisualAccessControl api = VisualAccessControl.naive();
        Caller alice = api.user("alice");
        api.user("bob");
        api.order("3f9c1e2a-77d0-4b8e-9a51-0c6b2f4d8e11", "bob", 120);

        // bob pasted a link to his order into a shared chat
        api.get(alice, "3f9c1e2a-77d0-4b8e-9a51-0c6b2f4d8e11");

        // Only the ownership check makes the id harmless to know.
        api.enforce(Defense.OWNER_ON_READ);
        api.get(alice, "3f9c1e2a-77d0-4b8e-9a51-0c6b2f4d8e11");
        api.report();
    }
}
