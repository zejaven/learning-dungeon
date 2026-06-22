import visual.VisualTransactionalEventListener;
import visual.VisualTransactionalEventListener.Phase;

public class Playground {
    public static void main(String[] args) {
        VisualTransactionalEventListener app = new VisualTransactionalEventListener("inventory");

        app.listener("StockGuard", Phase.BEFORE_COMMIT);
        app.listener("RollbackAudit", Phase.AFTER_ROLLBACK);

        app.transactional("reserveStock")
                .persist("reservation-77", "PENDING")
                .publish("StockReserved")
                .listenerFails("StockGuard", "OutOfStockException")
                .commit();
    }
}
