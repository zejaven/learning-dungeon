import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // The obvious design: one row, one number, UPDATE it on every operation.
        VisualBalanceLedger account = VisualBalanceLedger.inPlace();

        account.credit(1000, "salary");
        account.debit(250, "groceries");

        // Reading is as cheap as a read can be: one row, one column.
        account.readBalance();

        // Now the accountant asks the only question that matters.
        account.explain();
        account.audit();

        account.report();
        System.out.println("Fast, simple, and unable to answer 'why?'.");
    }
}
