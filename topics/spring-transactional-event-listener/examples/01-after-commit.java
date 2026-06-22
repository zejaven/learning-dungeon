import visual.VisualTransactionalEventListener;
import visual.VisualTransactionalEventListener.Phase;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");

        // In real Spring, AFTER_COMMIT is the default phase.
        app.listener("EmailReceipt", Phase.AFTER_COMMIT);

        app.transactional("placeOrder")
                .persist("order-101", "PAID")
                .publish("OrderPlaced")
                .commit();
    }
}
