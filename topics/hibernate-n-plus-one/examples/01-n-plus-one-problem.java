import visual.VisualNPlusOne;

/**
 * The N+1 problem: one root query to load the orders, then one extra SELECT
 * for every order whose lines collection is touched lazily. 3 orders => 1 + 3.
 */
public class Playground {
    public static void main(String[] args) {
        VisualNPlusOne db = new VisualNPlusOne("shop");

        // 1 query: load the parent rows. Their `lines` collections stay proxies.
        db.loadParents("orders", "lines", 3, 2);

        // Iterating and reading each order's lines fires one SELECT per order.
        db.accessAllChildren();
    }
}
