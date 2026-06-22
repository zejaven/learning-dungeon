import visual.VisualSpringTransaction;

public class Playground {
    public static void main(String[] args) {
        VisualSpringTransaction app = new VisualSpringTransaction("imports");

        // rollbackFor changes Spring's rule for this method. Now the checked
        // IOException marks the transaction rollback-only.
        app.transactional("importReport")
                .rollbackFor("java.io.IOException")
                .persist("report-2", "PARSED")
                .throwChecked("java.io.IOException")
                .complete();

        System.out.println("rollbackFor makes the checked exception roll back.");
    }
}
