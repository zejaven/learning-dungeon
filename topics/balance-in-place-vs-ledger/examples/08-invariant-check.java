import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // "The balance must never go below zero" is a rule about the fold, and
        // appending does not know about the fold.
        VisualBalanceLedger account = VisualBalanceLedger.appendOnly();
        account.credit(500, "opening");

        // One at a time, the check works exactly as written.
        account.debitWithFloor(300, 0, "rent");
        account.debitWithFloor(300, 0, "rent again");

        // Two withdrawals at once both read 200, both pass the check, and both
        // append. Nothing conflicted, and the account is overdrawn.
        account.debitConcurrentlyWithFloor(200, 0, "withdrawal");

        account.readBalance();
        account.explain();
        account.report();
        System.out.println("Append-only kills the lost update, not the check-then-act race.");
    }
}
