import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // Two operations on the same account at the same moment.
        // In place, that is a read-modify-write of one row.
        VisualBalanceLedger mutable = VisualBalanceLedger.inPlace();
        mutable.credit(1000, "opening");
        mutable.creditConcurrently(100, "refund", 200, "cashback");
        mutable.readBalance();
        mutable.report();

        // The same overlap against an append-only ledger. Two INSERTs into two
        // different rows cannot overwrite each other, so nothing is lost.
        VisualBalanceLedger ledger = VisualBalanceLedger.appendOnly();
        ledger.credit(1000, "opening");
        ledger.creditConcurrently(100, "refund", 200, "cashback");
        ledger.readBalance();
        ledger.report();

        System.out.println("1200 vs 1300 — the difference is the write pattern, not the database.");
    }
}
