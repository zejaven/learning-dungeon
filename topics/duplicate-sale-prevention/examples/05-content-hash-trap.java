import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        // The tempting shortcut: no key from the client, just hash the body and
        // put a UNIQUE index on the hash.
        VisualSaleDedup db = VisualSaleDedup.dedupingByContentHash();

        // A real retry of one sale is still caught, which is why this survives
        // code review.
        db.receive("sale-1", "coffee", 250);
        db.receive("sale-1", "coffee", 250);

        // Then the next customer orders the same coffee for the same price.
        // Different sale, different key, identical body.
        db.receive("sale-2", "coffee", 250);

        db.report();
        System.out.println("Payload equality is not identity: the second customer's sale is gone.");
    }
}
