import visual.VisualTransactionPropagation;
import visual.VisualTransactionPropagation.Propagation;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionPropagation tx = new VisualTransactionPropagation();

        // SUPPORTS with no active transaction -> runs non-transactionally.
        tx.enter("loadDashboard", Propagation.SUPPORTS);

        // Start a real transaction, then read a report without one.
        tx.enter("placeOrder", Propagation.REQUIRED);

        // NOT_SUPPORTED suspends the current transaction and runs without one.
        tx.enter("readReport", Propagation.NOT_SUPPORTED);
        tx.commit(); // report method returns; the outer transaction resumes

        tx.commit(); // outer commits
        tx.commit(); // the SUPPORTS method returns last (non-transactional)
    }
}
