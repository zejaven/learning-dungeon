import visual.VisualTransactionPropagation;
import visual.VisualTransactionPropagation.Propagation;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionPropagation tx = new VisualTransactionPropagation();

        // Outer transaction.
        tx.enter("placeOrder", Propagation.REQUIRED);

        // Inner REQUIRED shares the same physical transaction.
        tx.enter("writeAudit", Propagation.REQUIRED);
        tx.rollback(); // inner throws: it marks the SHARED transaction rollback-only

        tx.commit(); // outer tries to commit -> UnexpectedRollbackException
    }
}
