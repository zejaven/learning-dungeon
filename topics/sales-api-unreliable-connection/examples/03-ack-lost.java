import visual.VisualSaleRegistration;

public class Playground {
    public static void main(String[] args) {
        VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");

        pos.recordSale("sale-1", "coffee", 250);

        // The hard case: the server registered the sale, but the response was
        // lost on the way BACK. The client sees a timeout — exactly what a lost
        // request looks like — and cannot tell the two apart.
        pos.attemptResponseLost("sale-1");

        // So it retries with the SAME key. The server finds the key in its
        // idempotency store, applies nothing, and replays the stored response.
        pos.attemptDelivered("sale-1");

        System.out.println("Sent twice, registered once: the key made the retry a no-op.");
    }
}
