import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();

        db.receive("sale-1", "coffee", 250);

        // A till is switched off with an unconfirmed sale still in its queue.
        db.advanceDays(3);

        // Meanwhile the retention job keeps only one day of dedup keys.
        db.purgeDedupKeys(1);

        // The till comes back and drains its queue with the original key —
        // which the server no longer recognises.
        db.receive("sale-1", "coffee", 250);

        db.report();
        System.out.println("Retention must outlast the longest retry, not the average one.");
    }
}
