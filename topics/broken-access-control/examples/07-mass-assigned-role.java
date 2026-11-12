import visual.VisualAccessControl;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

public class Playground {
    public static void main(String[] args) {
        // The role check on /admin is there. But PATCH /users/me binds the
        // whole JSON body onto the User entity -- role included.
        VisualAccessControl api = VisualAccessControl.naive();
        api.enforce(Defense.ROLE_CHECK);
        Caller alice = api.user("alice");
        api.order("8", "bob", 120);

        api.adminExport(alice);                                   // 403
        api.updateProfile(alice, "displayName", "Al", "role", "ADMIN");
        api.adminExport(alice);                                   // now passes
        api.report();

        // A request DTO that only has displayName: role cannot arrive.
        VisualAccessControl fixed = VisualAccessControl.secure();
        Caller mallory = fixed.user("mallory");
        fixed.updateProfile(mallory, "displayName", "M", "role", "ADMIN");
        fixed.adminExport(mallory);
        fixed.report();
    }
}
