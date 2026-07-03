import visual.VisualTransactionPropagation;
import visual.VisualTransactionPropagation.Propagation;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionPropagation tx = new VisualTransactionPropagation();

        // Outer transaction.
        tx.enter("placeOrder", Propagation.REQUIRED);

        // REQUIRES_NEW suspends the outer transaction and opens a fresh one.
        tx.enter("writeAudit", Propagation.REQUIRES_NEW);
        tx.commit(); // inner physical transaction commits independently, outer resumes

        tx.commit(); // outer commits on its own
    }
}
