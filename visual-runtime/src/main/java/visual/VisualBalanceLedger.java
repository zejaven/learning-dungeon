package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the two ways a balance/accounting service can keep
 * money: as <strong>one mutable number</strong> that every operation overwrites,
 * or as an <strong>append-only sequence of immutable entries</strong> whose fold
 * <em>is</em> the balance.
 *
 * <p>The storage model is chosen with a factory and never changes afterwards:
 * <ul>
 *   <li>{@link #inPlace()} — {@code UPDATE accounts SET balance = ?}. One row, one
 *       number, O(1) reads, and no record of how the number was reached;</li>
 *   <li>{@link #appendOnly()} — {@code INSERT INTO entries ...}. Nothing is ever
 *       overwritten, the balance is recomputed by folding the entries, and every
 *       read costs as much as the history is long;</li>
 *   <li>{@link #appendOnlyWithCachedBalance()} — the hybrid almost every real
 *       system converges on: the entries are the source of truth and a
 *       denormalized balance column serves the reads. It is only correct while
 *       both are written in <em>one</em> transaction.</li>
 * </ul>
 *
 * <p>The interesting behaviour is in what each model does under pressure:
 * {@link #creditConcurrently} shows the read-modify-write lost update that an
 * append cannot suffer; {@link #explain()} asks the storage to justify its own
 * number; {@link #correctLast} fixes a wrong posting either by silently retyping
 * the total or by appending a reversal; {@link #deleteEntry(int)} is the trap of
 * "fixing" a ledger with a {@code DELETE}; {@link #takeSnapshot()} bounds the read
 * cost; {@link #debitInSeparateTransactions} lets the cached balance drift away
 * from the entries; and {@link #debitConcurrentlyWithFloor} shows that
 * append-only storage does not enforce a business invariant by itself.
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the UI
 * can replay the stream without re-running the code. The model is intentionally
 * dependency-free.
 */
public class VisualBalanceLedger {

    /** Where the account's money physically lives. */
    public enum Storage {
        /** One mutable number: {@code UPDATE accounts SET balance = ?}. */
        IN_PLACE,
        /** Append-only entries; the balance is a fold over them. */
        LEDGER
    }

    private final Storage storage;
    /** LEDGER only: a denormalized balance column kept beside the entries. */
    private final boolean cached;

    /** The immutable operation records. Empty for {@link Storage#IN_PLACE}. */
    private final List<Entry> entries = new ArrayList<>();
    /** Operations being processed side by side (empty while they are serialized). */
    private final List<Flight> inFlight = new ArrayList<>();

    /** The number physically stored in a column: the balance, or its cached copy. */
    private int stored;
    /** Whether a balance column exists at all (false for a pure ledger). */
    private final boolean hasStoredColumn;

    private int nextSeq = 1;
    /** Sequence number the last snapshot was taken at; 0 while there is none. */
    private int snapshotAtSeq;
    private int snapshotBalance;

    /** Rows the last balance read had to touch, and where it read them from. */
    private int readRows;
    private String readSource = "none";

    /** The signed amount of the last operation — what a correction has to undo. */
    private int lastAmount;
    private String lastReason = "";

    private int operations;
    private int corrections;
    private int lostUpdates;
    private int rewrites;
    private int mutations;
    private int violations;

    private VisualBalanceLedger(Storage storage, boolean cached) {
        this.storage = storage;
        this.cached = cached;
        this.hasStoredColumn = storage == Storage.IN_PLACE || cached;
        Trace.event("ACCOUNT_OPENED",
                "Account opened with balance 0 — storage: " + storageEn(),
                "Счёт открыт с балансом 0 — хранение: " + storageRu(),
                List.of("balance"), state());
    }

    /** The balance is one mutable number; every operation overwrites it. */
    public static VisualBalanceLedger inPlace() {
        return new VisualBalanceLedger(Storage.IN_PLACE, false);
    }

    /** Operations are immutable entries; the balance is a fold over them. */
    public static VisualBalanceLedger appendOnly() {
        return new VisualBalanceLedger(Storage.LEDGER, false);
    }

    /** Entries are the source of truth, a denormalized balance column serves reads. */
    public static VisualBalanceLedger appendOnlyWithCachedBalance() {
        return new VisualBalanceLedger(Storage.LEDGER, true);
    }

    // ------------------------------------------------------------- operations

    /** Money in: {@code +amount}. */
    public void credit(int amount, String reason) {
        apply(amount, reason, "operation");
    }

    /** Money out: {@code -amount}. */
    public void debit(int amount, String reason) {
        apply(-amount, reason, "operation");
    }

    /**
     * Two operations reach two instances at the same moment. In-place storage does
     * a read-modify-write on one row and loses one of them; an append-only ledger
     * writes two independent rows and loses nothing.
     */
    public void creditConcurrently(int amountA, String reasonA, int amountB, String reasonB) {
        int seen = balance();
        inFlight.clear();
        inFlight.add(new Flight("tx-A", signed(amountA), seen, "read"));
        inFlight.add(new Flight("tx-B", signed(amountB), seen, "read"));
        Trace.event("CONCURRENT_OPERATIONS",
                "Two operations (" + signed(amountA) + " " + reasonA + ", " + signed(amountB) + " "
                        + reasonB + ") arrive at once; both read the balance " + seen
                        + " before either writes — " + concurrencyEn(),
                "Две операции (" + signed(amountA) + " " + reasonA + ", " + signed(amountB) + " "
                        + reasonB + ") приходят одновременно; обе читают баланс " + seen
                        + " до того, как хоть одна запишет — " + concurrencyRu(),
                List.of("flight:tx-A", "flight:tx-B"), state());

        if (storage == Storage.IN_PLACE) {
            int wroteA = seen + amountA;
            int wroteB = seen + amountB;
            // Both computed from the same starting value; the last writer wins.
            stored = wroteB;
            lastAmount = amountB;
            lastReason = reasonB;
            operations += 2;
            lostUpdates++;
            phase("tx-A", "discarded");
            phase("tx-B", "wrote");
            Trace.event("LOST_UPDATE",
                    "tx-A wrote " + wroteA + ", then tx-B wrote " + wroteB + " — both started from "
                            + seen + ", so " + signed(amountA) + " (" + reasonA + ") is simply gone. "
                            + "The balance should be " + (seen + amountA + amountB) + ", it is " + stored,
                    "tx-A записала " + wroteA + ", затем tx-B записала " + wroteB + " — обе стартовали "
                            + "от " + seen + ", поэтому " + signed(amountA) + " (" + reasonA
                            + ") просто исчезла. Баланс должен быть " + (seen + amountA + amountB)
                            + ", а он " + stored,
                    List.of("balance", "flight:tx-A"), state());
            return;
        }

        phase("tx-A", "appended");
        apply(amountA, reasonA, "operation");
        phase("tx-B", "appended");
        apply(amountB, reasonB, "operation");
    }

    // ------------------------------------------------------------------ reads

    /**
     * What a {@code GET /balance} costs. In-place reads one row; a pure ledger
     * folds every entry; a snapshot bounds the fold; a cached column makes it O(1)
     * again at the price of a second copy of the truth.
     *
     * @return the balance the service would answer with
     */
    public int readBalance() {
        int value = balance();
        String sourceEn;
        String sourceRu;
        if (storage == Storage.IN_PLACE) {
            readRows = 1;
            readSource = "column";
            sourceEn = "the balance column";
            sourceRu = "колонки balance";
        } else if (cached) {
            readRows = 1;
            readSource = "cached-column";
            sourceEn = "the cached balance column";
            sourceRu = "кэширующей колонки balance";
        } else if (snapshotAtSeq > 0) {
            readRows = entriesAfterSnapshot();
            readSource = "snapshot-fold";
            sourceEn = "the snapshot at #" + snapshotAtSeq + " plus the entries after it";
            sourceRu = "снимка на #" + snapshotAtSeq + " плюс записей после него";
        } else {
            readRows = entries.size();
            readSource = "fold";
            sourceEn = "a fold over every entry";
            sourceRu = "свёртки по всем записям";
        }
        Trace.event("BALANCE_READ",
                "GET /balance -> " + value + ", read from " + sourceEn + " — " + readRows
                        + " row(s) touched",
                "GET /balance -> " + value + ", читается из " + sourceRu + " — затронуто строк: "
                        + readRows,
                List.of("balance"), state());
        return value;
    }

    /** The auditor's question: "why is the balance what it is?" */
    public void explain() {
        int value = balance();
        if (storage == Storage.IN_PLACE) {
            Trace.event("HISTORY_UNAVAILABLE",
                    "'Why is the balance " + value + "?' — the column holds one number and no record "
                            + "of how it got there. After " + operations + " overwrite(s) the answer "
                            + "is not stored anywhere",
                    "«Почему баланс " + value + "?» — в колонке лежит одно число и никакой записи о "
                            + "том, как оно получилось. После " + operations + " перезаписи(ей) ответ "
                            + "нигде не хранится",
                    List.of("balance"), state());
            return;
        }
        Trace.event("HISTORY_EXPLAINED",
                "'Why is the balance " + value + "?' — " + itemized() + " = " + value
                        + ". Every operation is still there, in order, with its reason",
                "«Почему баланс " + value + "?» — " + itemized() + " = " + value
                        + ". Каждая операция на месте, по порядку и со своей причиной",
                List.of("balance"), state());
    }

    /**
     * Recomputes the balance from the entries and compares it with whatever number
     * is stored. In-place storage has nothing to recompute from.
     */
    public void audit() {
        if (storage == Storage.IN_PLACE) {
            Trace.event("REBUILD_IMPOSSIBLE",
                    "Nothing to rebuild from: the balance " + stored + " is its own only evidence. "
                            + "If it is wrong, no query in this database can prove it",
                    "Восстанавливать не из чего: баланс " + stored + " — единственное доказательство "
                            + "самого себя. Если он неверен, ни один запрос в этой базе этого не покажет",
                    List.of("balance"), state());
            return;
        }
        int recomputed = computed();
        if (cached && recomputed != stored) {
            Trace.event("DRIFT_DETECTED",
                    "The cached balance column says " + stored + ", replaying the entries gives "
                            + recomputed + " — a drift of " + signed(stored - recomputed)
                            + ". The entries are the source of truth, so the column is the wrong one",
                    "Кэширующая колонка баланса говорит " + stored + ", а прогон записей даёт "
                            + recomputed + " — расхождение " + signed(stored - recomputed)
                            + ". Источник истины — записи, значит неправа колонка",
                    List.of("balance", "stored"), state());
            return;
        }
        Trace.event("BALANCE_REBUILT",
                "Replaying " + entries.size() + " entry(ies)" + (snapshotAtSeq > 0
                        ? " from the snapshot at #" + snapshotAtSeq : "") + " gives " + recomputed
                        + (mutations > 0
                        ? " — but " + mutations + " entry(ies) were deleted, so this number is not "
                        + "the history, it is what is left of it"
                        : " — the balance is a derived value and can always be recomputed"),
                "Прогон записей (" + entries.size() + ")" + (snapshotAtSeq > 0
                        ? " от снимка на #" + snapshotAtSeq : "") + " даёт " + recomputed
                        + (mutations > 0
                        ? " — но записей удалено: " + mutations + ", поэтому это число уже не история, "
                        + "а то, что от неё осталось"
                        : " — баланс является производным значением и всегда пересчитывается"),
                List.of("balance"), state());
    }

    // ------------------------------------------------------------ corrections

    /**
     * The last operation was posted with the wrong amount. In-place storage can
     * only retype the total; a ledger appends a reversal and the corrected entry.
     *
     * <p>{@code correctedAmount} is unsigned — it keeps the direction of the
     * operation being corrected. Note that in the in-place case the right amount
     * has to come from <em>outside</em> the database (a support ticket, a receipt):
     * the database itself no longer knows what was posted.
     */
    public void correctLast(int correctedAmount, String reason) {
        if (operations == 0) {
            return;
        }
        int corrected = lastAmount >= 0 ? correctedAmount : -correctedAmount;

        if (storage == Storage.IN_PLACE) {
            int before = stored;
            stored = stored - lastAmount + corrected;
            rewrites++;
            Trace.event("HISTORY_OVERWRITTEN",
                    "UPDATE accounts SET balance = " + stored + " (was " + before + "): " + reason
                            + ". The number is right again and there is no evidence anywhere that it "
                            + "was ever wrong — no old value, no correction, no who and no when",
                    "UPDATE accounts SET balance = " + stored + " (было " + before + "): " + reason
                            + ". Число снова верное, и нигде нет свидетельства, что оно было неверным — "
                            + "ни старого значения, ни поправки, ни кто, ни когда",
                    List.of("balance"), state());
            return;
        }

        Entry reversal = new Entry(nextSeq++, -lastAmount, "reversal of " + lastReason, "reversal");
        entries.add(reversal);
        Entry fixed = new Entry(nextSeq++, corrected, reason, "correction");
        entries.add(fixed);
        if (cached) {
            stored += reversal.amount + fixed.amount;
        }
        corrections++;
        lastAmount = corrected;
        lastReason = reason;
        Trace.event("CORRECTION_APPENDED",
                "Two new entries: #" + reversal.seq + " " + signed(reversal.amount)
                        + " reverses the wrong posting and #" + fixed.seq + " " + signed(fixed.amount)
                        + " posts the right one. The mistake stays in the book, the balance is "
                        + balance() + " — this is what an accountant means by a correcting entry",
                "Две новые записи: #" + reversal.seq + " " + signed(reversal.amount)
                        + " сторнирует ошибочную проводку, а #" + fixed.seq + " " + signed(fixed.amount)
                        + " проводит правильную. Ошибка остаётся в книге, баланс — " + balance()
                        + "; именно это бухгалтер называет корректирующей проводкой",
                List.of("balance", "entry:" + reversal.seq, "entry:" + fixed.seq), state());
    }

    /**
     * The trap: "fixing" the books with a {@code DELETE}. Append-only is a policy,
     * not a database feature — nothing except a permission stops this statement.
     */
    public void deleteEntry(int seq) {
        if (storage == Storage.IN_PLACE) {
            Trace.event("HISTORY_UNAVAILABLE",
                    "There is nothing to delete: in-place storage never wrote the history down, so "
                            + "the balance " + balance() + " has already lost every operation behind it",
                    "Удалять нечего: при хранении «на месте» история никогда не записывалась, поэтому "
                            + "баланс " + balance() + " уже потерял все стоящие за ним операции",
                    List.of("balance"), state());
            return;
        }
        Entry removed = null;
        for (Entry entry : entries) {
            if (entry.seq == seq) {
                removed = entry;
            }
        }
        if (removed == null) {
            return;
        }
        entries.remove(removed);
        mutations++;
        Trace.event("LEDGER_MUTATED",
                "DELETE FROM entries WHERE seq = " + seq + " removed " + signed(removed.amount) + " "
                        + removed.reason + ". Replaying the book now gives " + computed()
                        + " and it looks perfectly consistent — append-only is a policy, not a "
                        + "guarantee, unless permissions and a hash chain enforce it",
                "DELETE FROM entries WHERE seq = " + seq + " удалил " + signed(removed.amount) + " "
                        + removed.reason + ". Прогон книги теперь даёт " + computed()
                        + ", и выглядит это совершенно согласованно — append-only является политикой, "
                        + "а не гарантией, пока её не обеспечат права доступа и цепочка хэшей",
                List.of("balance"), state());
    }

    // ------------------------------------------------------ cost and drift

    /**
     * Stores the balance at the current point so later reads fold only the entries
     * after it — the standard answer to "the fold gets slower every year".
     */
    public void takeSnapshot() {
        if (storage == Storage.IN_PLACE) {
            Trace.event("SNAPSHOT_TAKEN",
                    "There is nothing to snapshot: in-place storage IS a snapshot, kept up to date "
                            + "and with the journal thrown away. Balance " + stored,
                    "Снимать нечего: хранение «на месте» И ЕСТЬ снимок, который поддерживают "
                            + "актуальным, выбросив журнал. Баланс " + stored,
                    List.of("balance"), state());
            return;
        }
        // Fold first, then move the marker: computed() folds from the marker.
        snapshotBalance = computed();
        snapshotAtSeq = nextSeq - 1;
        Trace.event("SNAPSHOT_TAKEN",
                "Snapshot: balance " + snapshotBalance + " as of entry #" + snapshotAtSeq
                        + ". Reads now fold only what came after it, and the entries are still there "
                        + "— a snapshot is a cache of the fold, never a replacement for the history",
                "Снимок: баланс " + snapshotBalance + " по состоянию на запись #" + snapshotAtSeq
                        + ". Чтения теперь сворачивают только то, что было после, а записи никуда не "
                        + "делись — снимок кэширует свёртку и никогда не заменяет историю",
                List.of("snapshot", "balance"), state());
    }

    /**
     * The hybrid's failure mode: the entry is committed, the cached balance column
     * is updated by a second transaction, and that transaction never runs.
     */
    public void debitInSeparateTransactions(int amount, String reason) {
        if (storage == Storage.IN_PLACE || !cached) {
            // There is only one place the number lives, so nothing can drift.
            apply(-amount, reason, "operation");
            return;
        }
        Entry entry = new Entry(nextSeq++, -amount, reason, "operation");
        entries.add(entry);
        operations++;
        lastAmount = -amount;
        lastReason = reason;
        Trace.event("CACHE_DRIFT",
                "Transaction 1 appended #" + entry.seq + " " + signed(entry.amount) + " " + reason
                        + " and committed; transaction 2 never updated the balance column. Reads still "
                        + "answer " + stored + ", the entries say " + computed()
                        + " — a denormalized balance is only correct inside ONE transaction",
                "Транзакция 1 добавила запись #" + entry.seq + " " + signed(entry.amount) + " " + reason
                        + " и зафиксировалась; транзакция 2 так и не обновила колонку баланса. Чтения "
                        + "по-прежнему отвечают " + stored + ", а записи говорят " + computed()
                        + " — денормализованный баланс корректен только внутри ОДНОЙ транзакции",
                List.of("balance", "stored", "entry:" + entry.seq), state());
    }

    // -------------------------------------------------------------- invariants

    /**
     * A withdrawal that must not take the balance below {@code floor}. The rule
     * lives in the service: no storage model enforces it for you.
     *
     * @return {@code true} when the withdrawal was actually applied
     */
    public boolean debitWithFloor(int amount, int floor, String reason) {
        int current = balance();
        if (current - amount < floor) {
            Trace.event("WITHDRAWAL_REJECTED",
                    "-" + amount + " (" + reason + ") would take the balance from " + current + " to "
                            + (current - amount) + ", below the floor of " + floor
                            + " — the service refuses it. Reading the balance is now on the write path",
                    "-" + amount + " (" + reason + ") увёл бы баланс с " + current + " до "
                            + (current - amount) + ", ниже порога " + floor
                            + " — сервис отказывает. Чтение баланса теперь стоит на пути записи",
                    List.of("balance"), state());
            return false;
        }
        apply(-amount, reason, "operation");
        return true;
    }

    /**
     * Two withdrawals that each pass the floor check on their own, at the same
     * moment. Appending never conflicts — which is exactly why it cannot enforce
     * an invariant that depends on the balance.
     */
    public void debitConcurrentlyWithFloor(int amount, int floor, String reason) {
        int seen = balance();
        inFlight.clear();
        inFlight.add(new Flight("tx-A", "-" + amount, seen, "read"));
        inFlight.add(new Flight("tx-B", "-" + amount, seen, "read"));
        Trace.event("CONCURRENT_OPERATIONS",
                "Two withdrawals of " + amount + " (" + reason + ") arrive at once; both read the "
                        + "balance " + seen + " and both pass the check " + (seen - amount) + " >= "
                        + floor + " — " + concurrencyEn(),
                "Два списания по " + amount + " (" + reason + ") приходят одновременно; обе читают "
                        + "баланс " + seen + " и обе проходят проверку " + (seen - amount) + " >= "
                        + floor + " — " + concurrencyRu(),
                List.of("flight:tx-A", "flight:tx-B"), state());
        phase("tx-A", storage == Storage.IN_PLACE ? "discarded" : "appended");
        phase("tx-B", storage == Storage.IN_PLACE ? "wrote" : "appended");

        if (storage == Storage.IN_PLACE) {
            stored = seen - amount;
            lastAmount = -amount;
            lastReason = reason;
            operations += 2;
            lostUpdates++;
            Trace.event("LOST_UPDATE",
                    "Both wrote " + stored + ": the second UPDATE overwrote the first, so the account "
                            + "was debited twice and charged once. The balance never breaks the floor "
                            + "here — it just stops matching reality",
                    "Обе записали " + stored + ": второй UPDATE перезаписал первый, поэтому со счёта "
                            + "списали дважды, а сняли один раз. Порог здесь не нарушен — просто "
                            + "баланс перестал соответствовать действительности",
                    List.of("balance", "flight:tx-A"), state());
            return;
        }

        apply(-amount, reason, "operation");
        apply(-amount, reason, "operation");
        if (balance() < floor) {
            violations++;
            Trace.event("INVARIANT_VIOLATED",
                    "Both entries were appended — appends never conflict — and the balance is now "
                            + balance() + ", below the floor of " + floor
                            + ". Append-only removes the lost update, it does NOT enforce a rule that "
                            + "depends on the balance: that needs a lock, a version, or a conditional insert",
                    "Обе записи добавлены — вставки между собой не конфликтуют — и баланс теперь "
                            + balance() + ", ниже порога " + floor
                            + ". Append-only убирает потерянное обновление, но НЕ обеспечивает правило, "
                            + "зависящее от баланса: для этого нужны блокировка, версия или условная вставка",
                    List.of("balance"), state());
        }
    }

    /** Prints the audit an accountant would run at the end of the day. */
    public void report() {
        Trace.event("BOOK_AUDIT",
                "Audit: balance " + balance() + ", entries kept " + entries.size()
                        + "; operations " + operations + ", corrections " + corrections
                        + ", lost updates " + lostUpdates + ", silent rewrites " + rewrites
                        + ", deleted entries " + mutations + ", broken invariants " + violations,
                "Сверка: баланс " + balance() + ", сохранено записей " + entries.size()
                        + "; операций " + operations + ", корректировок " + corrections
                        + ", потерянных обновлений " + lostUpdates + ", тихих перезаписей " + rewrites
                        + ", удалённых записей " + mutations + ", нарушенных инвариантов " + violations,
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    /** Applies one operation the way the configured storage model would. */
    private void apply(int amount, String reason, String kind) {
        lastAmount = amount;
        lastReason = reason;
        operations++;

        if (storage == Storage.IN_PLACE) {
            int before = stored;
            stored += amount;
            Trace.event("OPERATION_APPLIED",
                    "UPDATE accounts SET balance = " + stored + " (was " + before + ", "
                            + signed(amount) + " for " + reason
                            + ") — one row rewritten, and the previous value now exists nowhere",
                    "UPDATE accounts SET balance = " + stored + " (было " + before + ", "
                            + signed(amount) + " за " + reason
                            + ") — переписана одна строка, и прежнего значения больше нигде нет",
                    List.of("balance"), state());
            return;
        }

        Entry entry = new Entry(nextSeq++, amount, reason, kind);
        entries.add(entry);
        if (cached) {
            stored += amount;
        }
        Trace.event("ENTRY_APPENDED",
                "INSERT INTO entries (#" + entry.seq + ", " + signed(amount) + ", " + reason
                        + ")" + (cached ? " and the balance column, in ONE transaction" : "")
                        + " — nothing was overwritten; the balance is now " + balance(),
                "INSERT INTO entries (#" + entry.seq + ", " + signed(amount) + ", " + reason
                        + ")" + (cached ? " и колонка баланса — в ОДНОЙ транзакции" : "")
                        + " — ничего не перезаписано; баланс теперь " + balance(),
                List.of("balance", "entry:" + entry.seq), state());
    }

    /** What a read would answer right now. */
    private int balance() {
        return hasStoredColumn ? stored : computed();
    }

    /** The balance derived from the entries alone (from the snapshot, if any). */
    private int computed() {
        int sum = snapshotAtSeq > 0 ? snapshotBalance : 0;
        for (Entry entry : entries) {
            if (entry.seq > snapshotAtSeq) {
                sum += entry.amount;
            }
        }
        return sum;
    }

    private int entriesAfterSnapshot() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.seq > snapshotAtSeq) {
                count++;
            }
        }
        return count;
    }

    /** "+1000 salary, -250 groceries" — the itemized answer only a ledger can give. */
    private String itemized() {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(signed(entry.amount)).append(' ').append(entry.reason);
        }
        return sb.length() == 0 ? "no entries" : sb.toString();
    }

    private void phase(String flightId, String phase) {
        for (Flight flight : inFlight) {
            if (flight.id.equals(flightId)) {
                flight.phase = phase;
            }
        }
    }

    private static String signed(int amount) {
        return amount >= 0 ? "+" + amount : String.valueOf(amount);
    }

    private String concurrencyEn() {
        return storage == Storage.IN_PLACE
                ? "a read-modify-write of the same row"
                : "two independent INSERTs into different rows";
    }

    private String concurrencyRu() {
        return storage == Storage.IN_PLACE
                ? "это read-modify-write одной и той же строки"
                : "это два независимых INSERT в разные строки";
    }

    private String storageEn() {
        if (storage == Storage.IN_PLACE) {
            return "one mutable balance column (UPDATE in place)";
        }
        return cached
                ? "append-only entries plus a cached balance column"
                : "append-only entries, balance = SUM(entries)";
    }

    private String storageRu() {
        if (storage == Storage.IN_PLACE) {
            return "одна изменяемая колонка баланса (UPDATE на месте)";
        }
        return cached
                ? "неизменяемые записи плюс кэширующая колонка баланса"
                : "только неизменяемые записи, баланс = SUM(записи)";
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("storage", storage == Storage.IN_PLACE ? "in-place" : "ledger");
        s.put("cachedBalance", cached);
        s.put("balance", balance());
        if (hasStoredColumn) {
            s.put("stored", stored);
        }
        if (storage == Storage.LEDGER) {
            s.put("computed", computed());
            s.put("drift", cached ? stored - computed() : 0);
        }

        List<Object> rows = new ArrayList<>();
        int running = 0;
        for (Entry entry : entries) {
            running += entry.amount;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", entry.seq);
            m.put("amount", entry.amount);
            m.put("reason", entry.reason);
            m.put("kind", entry.kind);
            m.put("running", running);
            rows.add(m);
        }
        s.put("entries", rows);

        if (snapshotAtSeq > 0) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("atSeq", snapshotAtSeq);
            snap.put("balance", snapshotBalance);
            s.put("snapshot", snap);
        }

        Map<String, Object> read = new LinkedHashMap<>();
        read.put("rows", readRows);
        read.put("source", readSource);
        s.put("read", read);

        List<Object> flights = new ArrayList<>();
        for (Flight flight : inFlight) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", flight.id);
            m.put("action", flight.action);
            m.put("sawBalance", flight.sawBalance);
            m.put("phase", flight.phase);
            flights.add(m);
        }
        s.put("inFlight", flights);

        s.put("operations", operations);
        s.put("corrections", corrections);
        s.put("lostUpdates", lostUpdates);
        s.put("rewrites", rewrites);
        s.put("mutations", mutations);
        s.put("violations", violations);
        return s;
    }

    private static final class Entry {
        final int seq;
        final int amount;
        final String reason;
        /** operation | reversal | correction */
        final String kind;

        Entry(int seq, int amount, String reason, String kind) {
            this.seq = seq;
            this.amount = amount;
            this.reason = reason;
            this.kind = kind;
        }
    }

    private static final class Flight {
        final String id;
        final String action;
        final int sawBalance;
        /** read | wrote | appended | discarded */
        String phase;

        Flight(String id, String action, int sawBalance, String phase) {
            this.id = id;
            this.action = action;
            this.sawBalance = sawBalance;
            this.phase = phase;
        }
    }
}
