import visual.VisualSpringTransaction;

public class Playground {
    public static void main(String[] args) {
        VisualSpringTransaction app = new VisualSpringTransaction("imports");

        // Checked exceptions do not roll back by default. The exception still
        // leaves the method, but Spring's default rule allows commit.
        app.transactional("importReport")
                .persist("report-1", "PARSED")
                .throwChecked("java.io.IOException")
                .complete();

        System.out.println("The checked exception did not trigger rollback.");
    }
}
