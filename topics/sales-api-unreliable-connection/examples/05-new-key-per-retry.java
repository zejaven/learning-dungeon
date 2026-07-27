import visual.VisualSaleRegistration;

public class Playground {
    public static void main(String[] args) {
        VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");

        pos.recordSale("sale-a1", "coffee", 250);

        // Registered on the server; the client never learns it.
        pos.attemptResponseLost("sale-a1");

        // THE TRAP: the retry path re-creates the sale, so a fresh key is
        // generated. To the server this is simply a different sale.
        pos.recordSale("sale-a2", "coffee", 250);
        pos.attemptDelivered("sale-a2");

        // The customer paid once and is charged twice. The key must be minted
        // once per business action, not once per HTTP attempt.
        System.out.println("New key per retry = no idempotency at all.");
    }
}
