import visual.VisualTransactionPropagation;
import visual.VisualTransactionPropagation.Propagation;

public class Playground {
    public static void main(String[] args) {
        // MANDATORY with no active transaction -> propagation error.
        VisualTransactionPropagation noTx = new VisualTransactionPropagation();
        noTx.enter("sendInvoice", Propagation.MANDATORY); // no transaction to join

        // NEVER inside an active transaction -> propagation error.
        VisualTransactionPropagation withTx = new VisualTransactionPropagation();
        withTx.enter("placeOrder", Propagation.REQUIRED);
        withTx.enter("readCache", Propagation.NEVER); // forbidden inside a transaction
        withTx.commit(); // outer still commits
    }
}
