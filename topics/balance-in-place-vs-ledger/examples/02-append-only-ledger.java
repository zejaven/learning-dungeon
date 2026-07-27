import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // The same two operations, stored as immutable entries instead.
        // The balance is not stored at all — it is derived.
        VisualBalanceLedger account = VisualBalanceLedger.appendOnly();

        account.credit(1000, "salary");
        account.debit(250, "groceries");

        // Reading now costs one row per operation ever recorded.
        account.readBalance();

        // The same question, and this time there is an answer.
        account.explain();
        account.audit();

        account.report();
        System.out.println("Same balance, plus the reason for every unit of it.");
    }
}
