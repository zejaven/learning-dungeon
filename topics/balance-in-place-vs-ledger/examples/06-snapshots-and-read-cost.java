import visual.VisualBalanceLedger;

public class Playground {
    public static void main(String[] args) {
        // The ledger's real cost: every balance read folds the whole history,
        // and the history only grows.
        VisualBalanceLedger account = VisualBalanceLedger.appendOnly();
        for (int day = 1; day <= 8; day++) {
            account.credit(100, "day " + day);
        }
        account.readBalance();

        // A snapshot stores the fold up to a point. Reads after it only need the
        // entries that came later; the entries themselves are never deleted.
        account.takeSnapshot();
        account.credit(100, "day 9");
        account.readBalance();

        account.report();
        System.out.println("A snapshot caches the fold. It never replaces the history.");
    }
}
