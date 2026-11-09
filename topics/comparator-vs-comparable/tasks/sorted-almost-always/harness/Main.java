import visual.TestKit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hidden tests for the overflowing comparators task. */
public class Main {

    private static int byBalance(int left, int right) {
        return sign(Accounts.BY_BALANCE, new Account("l", left, 0), new Account("r", right, 0));
    }

    private static int byOpenedAt(long left, long right) {
        return sign(Accounts.BY_OPENED_AT, new Account("l", 0, left), new Account("r", 0, right));
    }

    private static int sign(Comparator<Account> order, Account left, Account right) {
        return Integer.signum(order.compare(left, right));
    }

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("balance: ordinary values", -1, () -> byBalance(10, 20)),
                TestKit.test("balance: equal values", 0, () -> byBalance(20, 20)),
                TestKit.test("balance: MIN_VALUE comes before 1", -1, () -> byBalance(Integer.MIN_VALUE, 1)),
                TestKit.test("balance: MAX_VALUE comes after -1", 1, () -> byBalance(Integer.MAX_VALUE, -1)),
                TestKit.test("balance: a list with extreme values sorts", List.of("debt", "zero", "rich"), () -> {
                    List<Account> accounts = new ArrayList<>(List.of(
                            new Account("rich", Integer.MAX_VALUE, 0),
                            new Account("debt", Integer.MIN_VALUE, 0),
                            new Account("zero", 0, 0)));
                    accounts.sort(Accounts.BY_BALANCE);
                    return accounts.stream().map(a -> a.id).toList();
                }),
                TestKit.test("opened at: ordinary values", -1, () -> byOpenedAt(1_000, 2_000)),
                TestKit.test("opened at: exactly 2^32 ms apart", -1, () -> byOpenedAt(0, 1L << 32)),
                TestKit.test("opened at: the extremes of long", -1, () -> byOpenedAt(Long.MIN_VALUE, Long.MAX_VALUE))
        );
    }
}
