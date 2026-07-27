import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // A customer disputes their balance. Both services ran the same
        // operations; only one of them can show its work.
        VisualBalanceLedger mutable = VisualBalanceLedger.inPlace();
        mutable.credit(1200, "invoice 41 paid");
        mutable.debit(300, "refund to customer");
        mutable.debit(45, "processing fee");
        mutable.explain();

        VisualBalanceLedger ledger = VisualBalanceLedger.appendOnly();
        ledger.credit(1200, "invoice 41 paid");
        ledger.debit(300, "refund to customer");
        ledger.debit(45, "processing fee");
        ledger.explain();

        System.out.println("Both say 855. Only one of them can prove it.");
    }
}
