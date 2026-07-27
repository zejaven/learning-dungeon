import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();

        // The dedup row is committed in its own transaction ("reserve the key
        // first, then do the work") and the process dies in between.
        db.receiveSplitCommit("sale-1", "coffee", 250);

        // The client retries, as it should. The key is there, so the server
        // answers "already registered" — for a sale that was never written.
        db.receive("sale-1", "coffee", 250);

        db.report();
        System.out.println("The dedup row and the sale must be committed together or not at all.");
    }
}
