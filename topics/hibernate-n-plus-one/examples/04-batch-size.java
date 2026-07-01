import visual.VisualNPlusOne;

/**
 * @BatchSize (or hibernate.default_batch_fetch_size) keeps lazy loading but
 * groups the child selects: instead of one query per parent, Hibernate uses
 * WHERE parent_id IN (?, ?, ...). 5 orders with batch size 2 => ceil(5/2) = 3
 * child queries instead of 5.
 */
public class Playground {
    public static void main(String[] args) {
        VisualNPlusOne db = new VisualNPlusOne("shop");

        db.setBatchSize(2);

        // 1 root query for the 5 orders.
        db.loadParents("orders", "lines", 5, 2);

        // Lazy access, but batched: 3 IN (...) queries, not 5 single ones.
        db.accessAllChildren();
    }
}
