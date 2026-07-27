import visual.VisualSaleRegistration;

public class Playground {
    public static void main(String[] args) {
        VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");

        // The CLIENT mints the idempotency key and writes the sale into its own
        // durable local queue BEFORE it touches the network. The sale exists
        // even if the device dies one millisecond later.
        pos.recordSale("sale-1", "coffee", 250);

        // A full round trip: the server registers the sale under that key,
        // stores the response, and the acknowledgement comes back.
        pos.attemptDelivered("sale-1");

        System.out.println("Recorded locally first, registered once, confirmed once.");
    }
}
