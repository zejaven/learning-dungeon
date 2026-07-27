import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        // INSERT ... ON CONFLICT (dedup_key) DO NOTHING — one statement, so the
        // check and the write cannot come apart.
        VisualSaleDedup db = VisualSaleDedup.withUpsert();

        // "sale-1" is the sale's identity: the client minted it once, when the
        // sale happened, and every delivery of that sale carries it unchanged.
        db.receive("sale-1", "coffee", 250);

        // The retry affects zero rows and gets the stored response back, so the
        // client can still print a receipt.
        db.receive("sale-1", "coffee", 250);

        // A different sale has a different key and is registered normally.
        db.receive("sale-2", "bagel", 180);

        db.report();
        System.out.println("Three requests, two sales: identity is what makes a retry safe.");
    }
}
