import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        // The "Export all orders" button is rendered only for admins.
        // The endpoint behind it checks nothing.
        VisualAccessControl api = VisualAccessControl.naive();
        Caller alice = api.user("alice");
        Caller root = api.admin("root");
        api.order("7", "alice", 30);
        api.order("8", "bob", 120);

        api.adminExport(alice);   // she read the JS bundle and typed the URL

        // The role is checked on the server, for every request.
        api.enforce(Defense.ROLE_CHECK);
        api.adminExport(alice);
        api.adminExport(root);
        api.report();
    }
}
