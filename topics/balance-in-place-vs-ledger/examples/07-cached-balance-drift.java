import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // The design almost every real system lands on: immutable entries as the
        // source of truth, plus a denormalized balance column for O(1) reads.
        VisualBalanceLedger account = VisualBalanceLedger.appendOnlyWithCachedBalance();

        // While the entry and the column are written in one transaction, the two
        // copies of the truth cannot disagree.
        account.credit(1000, "salary");
        account.debit(250, "groceries");
        account.readBalance();
        account.audit();

        // Split them into two transactions and the second one can simply not run.
        account.debitInSeparateTransactions(90, "card fee");
        account.readBalance();

        // Only the ledger can tell you this happened — that is the other half of
        // what the entries buy you.
        account.audit();

        account.report();
        System.out.println("Two copies of the truth are safe only inside one transaction.");
    }
}
