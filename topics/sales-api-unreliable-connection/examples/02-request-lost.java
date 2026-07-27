import visual.VisualSaleRegistration;

public class Playground {
    public static void main(String[] args) {
        VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");

        pos.recordSale("sale-1", "coffee", 250);

        // The connection dies on the way OUT: the request never reached the
        // server, so nothing was registered there. The sale stays pending in
        // the local queue and a retry is scheduled.
        pos.attemptRequestLost("sale-1");

        // The retry carries the SAME idempotency key. The key is unused on the
        // server, so this is the first real registration of the sale.
        pos.attemptDelivered("sale-1");

        System.out.println("A request that never arrived is harmless: retry, same key, one sale.");
    }
}
