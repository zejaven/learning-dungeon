import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        VisualSaleDedup db = VisualSaleDedup.withUpsert();

        db.receive("sale-1", "coffee", 250);

        // The same key with a different body: the client reused a key for a new
        // sale. Replaying the stored response would hide that second sale
        // forever, so the server refuses instead.
        db.receive("sale-1", "cake", 500);

        db.report();
        System.out.println("A known key with a different body is a bug to surface, not a duplicate to swallow.");
    }
}
