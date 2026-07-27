import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // An operator posts an invoice with the wrong amount: 2500 instead of 250.
        // In place, the only repair is to type the right total in. Note that the
        // right amount has to come from outside the database — a support ticket,
        // a paper receipt — because the database no longer knows what was posted.
        VisualBalanceLedger mutable = VisualBalanceLedger.inPlace();
        mutable.credit(1000, "salary");
        mutable.debit(2500, "invoice 77");
        mutable.correctLast(250, "invoice 77 was 250, not 2500");
        mutable.explain();
        mutable.report();

        // A ledger cannot edit the past, so it appends a reversal and the correct
        // entry. The mistake stays in the book, which is the point.
        VisualBalanceLedger ledger = VisualBalanceLedger.appendOnly();
        ledger.credit(1000, "salary");
        ledger.debit(2500, "invoice 77");
        ledger.correctLast(250, "invoice 77 was 250, not 2500");
        ledger.explain();

        // ...unless someone decides to "clean up" the book with a DELETE.
        ledger.deleteEntry(2);
        ledger.audit();
        ledger.report();

        System.out.println("Immutability is a policy that permissions have to enforce.");
    }
}
