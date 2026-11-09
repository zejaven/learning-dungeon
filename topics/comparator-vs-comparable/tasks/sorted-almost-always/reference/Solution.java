import java.util.Comparator;

/** A customer account. Balances can be deeply negative. */
final class Account {
    final String id;
    final int balance;
    /** Opened-at time in milliseconds. */
    final long openedAt;

    Account(String id, int balance, long openedAt) {
        this.id = id;
        this.balance = balance;
        this.openedAt = openedAt;
    }
}

final class Accounts {
    /** Lowest balance first. */
    static final Comparator<Account> BY_BALANCE = (a, b) -> Integer.compare(a.balance, b.balance);

    /** Oldest account first. */
    static final Comparator<Account> BY_OPENED_AT = (a, b) -> Long.compare(a.openedAt, b.openedAt);
}
