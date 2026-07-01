import visual.VisualNPlusOne;

/**
 * An @EntityGraph (or @NamedEntityGraph) marks which associations to load
 * eagerly for one query, without changing the mapping's default fetch type.
 * Like JOIN FETCH, everything arrives in a single statement.
 */
public class Playground {
    public static void main(String[] args) {
        VisualNPlusOne db = new VisualNPlusOne("shop");

        // 1 query, driven by the entity graph attached to the query.
        db.loadParentsWithEntityGraph("orders", "lines", 3, 2);

        // Nothing lazy is left to load.
        db.accessAllChildren();
    }
}
