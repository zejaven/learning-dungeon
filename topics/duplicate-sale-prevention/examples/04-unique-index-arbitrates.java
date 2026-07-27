import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        // Same guard, different owner: a UNIQUE index on dedup_key. There is no
        // separate check any more — the INSERT itself is the check.
        VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();

        // The exact race that defeated check-then-insert.
        db.receiveConcurrently("sale-1", "coffee", 250);

        db.report();
        System.out.println("Two instances, one winner: only the database can arbitrate this.");
    }
}
