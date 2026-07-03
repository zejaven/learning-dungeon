import visual.VisualTransactionPropagation;
import visual.VisualTransactionPropagation.Propagation;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionPropagation tx = new VisualTransactionPropagation();

        // Outer @Transactional(REQUIRED) starts one physical transaction.
        tx.enter("placeOrder", Propagation.REQUIRED);

        // Inner @Transactional(REQUIRED) finds an active transaction and joins it.
        tx.enter("writeAudit", Propagation.REQUIRED);
        tx.commit(); // inner returns; nothing is committed yet

        tx.commit(); // outer commits the single shared physical transaction
    }
}
