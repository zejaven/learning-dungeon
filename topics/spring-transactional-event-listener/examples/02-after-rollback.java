import visual.VisualTransactionalEventListener;
import visual.VisualTransactionalEventListener.Phase;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");

        app.listener("EmailReceipt", Phase.AFTER_COMMIT);
        app.listener("RollbackAudit", Phase.AFTER_ROLLBACK);

        app.transactional("placeOrder")
                .persist("order-102", "NEW")
                .publish("OrderPlaced")
                .rollback("payment declined");
    }
}
