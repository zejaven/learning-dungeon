import visual.VisualTransactionalEventListener;
import visual.VisualTransactionalEventListener.Phase;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");

        app.listener("EmailReceipt", Phase.AFTER_COMMIT);
        app.listener("ImmediateAudit", Phase.AFTER_COMMIT, true);

        app.publishOutsideTransaction("OrderPlaced");
    }
}
