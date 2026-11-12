package visual;

import org.junit.jupiter.api.Test;
import visual.VisualAccessControl.Caller;
import visual.VisualAccessControl.Defense;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualAccessControlTest {

    private String captureTrace(Runnable body) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void readingAnotherUsersOrderByIdIsAHorizontalBreachWithoutAnOwnershipCheck() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.naive();
            Caller alice = api.user("alice");
            api.user("bob");
            api.order("42", "bob", 100);
            api.get(alice, "42");
            api.report();
        });
        assertTrue(out.contains("OWNERSHIP_CHECK_MISSING"), out);
        assertTrue(out.contains("served that should NOT have been 1 (horizontal 1, vertical 0, mass assignment 0)"), out);
    }

    @Test
    void theOwnershipCheckRefusesWith404WhenForeignRecordsAreHidden() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.secure();
            Caller alice = api.user("alice");
            api.user("bob");
            api.order("42", "bob", 100);
            api.get(alice, "42");
            api.report();
        });
        assertTrue(out.contains("OWNERSHIP_DENIED"), out);
        assertTrue(out.contains("-> 404 refused (not-owner)"), out);
        assertTrue(out.contains("served that should NOT have been 0"), out);
    }

    @Test
    void withoutTheHidingDefenseTheRefusalIs403() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.naive();
            api.enforce(Defense.OWNER_ON_READ);
            Caller alice = api.user("alice");
            api.order("42", "bob", 100);
            api.get(alice, "42");
        });
        assertTrue(out.contains("-> 403 refused (not-owner)"), out);
    }

    @Test
    void aGuardedReadDoesNotGuardTheWrite() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.naive();
            api.enforce(Defense.OWNER_ON_READ);
            Caller alice = api.user("alice");
            api.order("42", "bob", 100);
            api.get(alice, "42");
            api.delete(alice, "42");
            api.report();
        });
        assertTrue(out.contains("OWNERSHIP_DENIED"), out);
        assertTrue(out.contains("DELETE /orders/42 as alice -> 204 served, BREACH: horizontal"), out);
    }

    @Test
    void anUnscopedListLeaksOtherUsersRowsAndAScopedOneIgnoresTheParameter() {
        String leaked = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.naive();
            Caller alice = api.user("alice");
            api.order("7", "alice", 10).order("42", "bob", 100).order("43", "bob", 50);
            api.listByOwner(alice, "bob");
            api.report();
        });
        assertTrue(leaked.contains("LIST_LEAKED"), leaked);
        assertTrue(leaked.contains("foreign rows leaked 2"), leaked);

        String scoped = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.secure();
            Caller alice = api.user("alice");
            api.order("7", "alice", 10).order("42", "bob", 100);
            api.listByOwner(alice, "bob");
            api.report();
        });
        assertTrue(scoped.contains("LIST_SCOPED"), scoped);
        assertTrue(scoped.contains("foreign rows leaked 0"), scoped);
    }

    @Test
    void anAdminEndpointWithoutARoleCheckIsVerticalEscalation() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.naive();
            Caller alice = api.user("alice");
            api.order("42", "bob", 100);
            api.adminExport(alice);
            api.enforce(Defense.ROLE_CHECK);
            api.adminExport(alice);
            api.report();
        });
        assertTrue(out.contains("ROLE_CHECK_MISSING"), out);
        assertTrue(out.contains("ROLE_DENIED"), out);
        assertTrue(out.contains("vertical 1"), out);
    }

    @Test
    void massAssigningTheRoleEscalatesAndTheAllowlistIgnoresIt() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.naive();
            api.enforce(Defense.ROLE_CHECK);
            Caller alice = api.user("alice");
            api.updateProfile(alice, "displayName", "Al", "role", "ADMIN");
            api.adminExport(alice);
            api.report();
        });
        assertTrue(out.contains("PRIVILEGE_ESCALATED"), out);
        assertTrue(out.contains("GET /admin/orders as alice -> 200 served"), out);
        assertFalse(out.contains("ROLE_DENIED"), out);

        String safe = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.secure();
            Caller alice = api.user("alice");
            api.updateProfile(alice, "role", "ADMIN");
            api.adminExport(alice);
        });
        assertTrue(safe.contains("FIELD_IGNORED"), safe);
        assertTrue(safe.contains("ROLE_DENIED"), safe);
    }

    @Test
    void aBodyIdRedirectsAWritePastTheOwnershipCheck() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.naive();
            api.enforce(Defense.OWNER_ON_WRITE);
            Caller alice = api.user("alice");
            api.order("7", "alice", 10).order("42", "bob", 100);
            api.update(alice, "7", "id", "42", "price", "1");
            api.report();
        });
        assertTrue(out.contains("BODY_ID_OVERRIDE"), out);
        assertTrue(out.contains("MASS_ASSIGNMENT"), out);
        assertTrue(out.contains("horizontal 1, vertical 0, mass assignment 1"), out);
    }

    @Test
    void aSecureApiServesTheOwnerWithoutBreaches() {
        String out = captureTrace(() -> {
            VisualAccessControl api = VisualAccessControl.secure();
            Caller alice = api.user("alice");
            api.order("7", "alice", 10);
            api.get(alice, "7");
            api.update(alice, "7", "note", "ring twice");
            api.report();
        });
        assertTrue(out.contains("HANDLER_RAN"), out);
        assertTrue(out.contains("served that should NOT have been 0"), out);
    }
}
