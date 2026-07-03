import visual.VisualTransactionPropagation;
import visual.VisualTransactionPropagation.Propagation;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionPropagation tx = new VisualTransactionPropagation();

        // Outer transaction imports a batch.
        tx.enter("importBatch", Propagation.REQUIRED);

        // NESTED opens a savepoint inside the SAME physical transaction.
        tx.enter("importRow", Propagation.NESTED);
        tx.rollback(); // only the savepoint is rolled back; the outer transaction survives

        tx.commit(); // outer still commits everything before the savepoint
    }
}
