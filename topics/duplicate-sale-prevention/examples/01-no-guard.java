import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        // The endpoint every service starts with: it just inserts.
        VisualSaleDedup db = VisualSaleDedup.withoutGuard();

        // One customer, one coffee, one sale — registered.
        db.receive("sale-1", "coffee", 250);

        // The same sale arrives a second time. Why does not matter: a client
        // timeout and retry, a redelivered message, a double-tapped button.
        db.receive("sale-1", "coffee", 250);

        db.report();
        System.out.println("One sale, two rows. The duplicate is created by the server, not the client.");
    }
}
