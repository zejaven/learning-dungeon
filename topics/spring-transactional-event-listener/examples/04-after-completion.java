import visual.VisualTransactionalEventListener;
import visual.VisualTransactionalEventListener.Phase;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");

        app.listener("CleanupCache", Phase.AFTER_COMPLETION);

        app.transactional("approveOrder")
                .persist("order-201", "APPROVED")
                .publish("OrderApproved")
                .commit();

        app.transactional("rejectOrder")
                .persist("order-202", "REJECTED")
                .publish("OrderRejected")
                .rollback("fraud check failed");
    }
}
