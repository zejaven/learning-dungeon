import visual.VisualTransactionalEventListener;
import visual.VisualTransactionalEventListener.Phase;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");

        app.listener("EmailReceipt", Phase.AFTER_COMMIT);

        app.transactional("placeOrder")
                .persist("order-301", "PAID")
                .publish("OrderPlaced")
                .listenerFails("EmailReceipt", "MailServerDownException")
                .commit();
    }
}
