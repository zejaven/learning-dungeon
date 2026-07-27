import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        VisualInjection app = VisualInjection.app();

        // The write is textbook correct: the value is bound, so it lands in
        // the column as those exact characters and nothing more.
        app.saveProfileBound("' OR '1'='1");

        // A later job reads that row back and concatenates it into a query.
        // There is no user input anywhere on this code path -- the value came
        // out of our own database -- and it is still an injection. Being
        // stored safely does not make a string safe to concatenate.
        app.auditSavedProfile();

        // The other way a real bind parameter still loses: the procedure it
        // is handed to builds SQL out of it with EXECUTE IMMEDIATE. The
        // concatenation just moved somewhere the driver cannot see.
        app.callReportProcedure("user' OR '1'='1");

        app.report();
        System.out.println("Binding protects one query, not the value's whole life.");
    }
}
