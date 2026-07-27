import visual.VisualSaleDedup;

public class Playground {
    public static void main(String[] args) {
        // The application-level guard: SELECT to see whether the key is known,
        // then INSERT if it is not.
        VisualSaleDedup db = VisualSaleDedup.withCheckThenInsert();

        // While requests are serialized this looks perfect.
        db.receive("sale-1", "coffee", 250);
        db.receive("sale-1", "coffee", 250);

        // Now a slow original and its retry land on two instances at once. Both
        // run the SELECT before either runs the INSERT, so both see "new".
        db.receiveConcurrently("sale-2", "bagel", 180);

        db.report();
        System.out.println("The gap between the check and the write is the whole bug.");
    }
}
