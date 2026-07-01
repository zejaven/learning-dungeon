import visual.VisualNPlusOne;

/**
 * JOIN FETCH fixes N+1: a single query loads orders and their lines together,
 * so touching the collections afterwards costs nothing.
 */
public class Playground {
    public static void main(String[] args) {
        VisualNPlusOne db = new VisualNPlusOne("shop");

        // 1 query: SELECT ... FROM orders JOIN lines ...
        db.loadParentsWithJoinFetch("orders", "lines", 3, 2);

        // Collections are already initialized: no additional SELECT.
        db.accessAllChildren();
    }
}
