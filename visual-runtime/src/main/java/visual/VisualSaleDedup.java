package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A <em>teaching model</em> of the <strong>server side</strong> of sale
 * registration: what actually stops a repeated request from becoming a second
 * sale, and how each guard fails.
 *
 * <p>The model owns two tables:
 * <ul>
 *   <li>the <em>sales table</em> — one row per registered sale, duplicates
 *       included, because the whole question is whether a duplicate row can be
 *       written at all;</li>
 *   <li>the <em>dedup index</em> — {@code dedup_key -> (stored response, day)},
 *       the record the guard consults.</li>
 * </ul>
 *
 * <p>The guard is chosen with a factory and never changes afterwards:
 * <ul>
 *   <li>{@link #withoutGuard()} — nothing deduplicates; every request inserts;</li>
 *   <li>{@link #withCheckThenInsert()} — the application does {@code SELECT}
 *       then {@code INSERT}: correct when requests are serialized, broken the
 *       moment two overlap;</li>
 *   <li>{@link #withUniqueIndex()} — a {@code UNIQUE} index on the dedup key:
 *       the write itself is the check, so the loser of a race gets a constraint
 *       violation instead of a second row;</li>
 *   <li>{@link #withUpsert()} — {@code INSERT ... ON CONFLICT DO NOTHING}: the
 *       same guarantee in one statement and no exception to catch;</li>
 *   <li>{@link #dedupingByContentHash()} — the trap: a unique index over a hash
 *       of the payload instead of an explicit identity, which silently drops a
 *       genuinely different sale that happens to look the same.</li>
 * </ul>
 *
 * <p>Two failure modes that have nothing to do with the guard itself are modelled
 * explicitly, because they are what breaks real systems:
 * {@link #receiveSplitCommit(String, String, int)} commits the dedup row in its
 * own transaction and dies before the sale row (the retry is then deduplicated
 * against a sale that does not exist), and {@link #purgeDedupKeys(int)} shows a
 * retention window shorter than the longest possible retry reopening the hole.
 *
 * <p>Every step emits a {@link Trace} event with a bilingual description so the
 * UI can replay the stream without re-running the code. The model is
 * intentionally dependency-free.
 */
public class VisualSaleDedup {

    /** The response stored for a key the first time its sale is registered. */
    private static final String CREATED = "201 created";

    /** How the server stops a repeated request from becoming a second sale. */
    public enum Guard {
        /** No guard at all: every request inserts a row. */
        NONE,
        /** Application-level {@code SELECT} followed by {@code INSERT} — racy. */
        CHECK_THEN_INSERT,
        /** A {@code UNIQUE} index on the dedup key; the INSERT is the check. */
        UNIQUE_INDEX,
        /** {@code INSERT ... ON CONFLICT (dedup_key) DO NOTHING} — one statement. */
        UPSERT
    }

    private final Guard guard;
    private final boolean dedupByContent;

    /** The dedup index. A List, not a Map, so a racy guard can hold two rows. */
    private final List<Entry> index = new ArrayList<>();
    /** The sales table: one row per registered sale, duplicates included. */
    private final List<Row> sales = new ArrayList<>();
    /** Requests currently being processed side by side (empty when serialized). */
    private final List<Flight> inFlight = new ArrayList<>();

    private int day;
    private int retentionDays;
    private int total;
    private int registered;
    private int blocked;
    private int duplicates;
    private int lost;

    /** The request being processed (null before the first one). */
    private Request request;

    private VisualSaleDedup(Guard guard, boolean dedupByContent) {
        this.guard = guard;
        this.dedupByContent = dedupByContent;
        Trace.event("LEDGER_READY",
                "Sales service ready — guard: " + guardEn()
                        + ". The sales table and the dedup index are empty",
                "Сервис продаж готов — защита: " + guardRu()
                        + ". Таблица продаж и индекс дедупликации пусты",
                List.of(), state());
    }

    /** No deduplication at all: every request that arrives inserts a sale row. */
    public static VisualSaleDedup withoutGuard() {
        return new VisualSaleDedup(Guard.NONE, false);
    }

    /** The application checks with a SELECT and then INSERTs — a check-then-act race. */
    public static VisualSaleDedup withCheckThenInsert() {
        return new VisualSaleDedup(Guard.CHECK_THEN_INSERT, false);
    }

    /** A UNIQUE index on the dedup key: the database arbitrates every race. */
    public static VisualSaleDedup withUniqueIndex() {
        return new VisualSaleDedup(Guard.UNIQUE_INDEX, false);
    }

    /** INSERT ... ON CONFLICT DO NOTHING: the same guarantee in one statement. */
    public static VisualSaleDedup withUpsert() {
        return new VisualSaleDedup(Guard.UPSERT, false);
    }

    /** The trap: dedup by a hash of the payload instead of an explicit identity. */
    public static VisualSaleDedup dedupingByContentHash() {
        return new VisualSaleDedup(Guard.UNIQUE_INDEX, true);
    }

    /**
     * One request reaching the service on its own, with nothing else in flight.
     *
     * @return {@code true} when this request actually inserted a sale row
     */
    public boolean receive(String key, String item, int amount) {
        inFlight.clear();
        startRequest(key, item, amount);
        Trace.event("REQUEST_RECEIVED",
                "POST /sales arrives with key '" + key + "' (" + item + ", " + amount + ")",
                "Приходит POST /sales с ключом '" + key + "' (" + item + ", " + amount + ")",
                List.of("request:" + key), state());
        return apply(key, item, amount, "req");
    }

    /**
     * The original request and its retry reach two instances at the same moment.
     * Only a guard that is enforced by the database survives this; an
     * application-level check lets both requests through.
     */
    public void receiveConcurrently(String key, String item, int amount) {
        startRequest(key, item, amount);
        inFlight.clear();
        inFlight.add(new Flight("req-A", key, "in flight"));
        inFlight.add(new Flight("req-B", key, "in flight"));
        Trace.event("CONCURRENT_ARRIVAL",
                "A slow original and its retry — both with key '" + key + "' — reach two "
                        + "instances at the same moment",
                "Медленный оригинал и его повтор — оба с ключом '" + key + "' — приходят "
                        + "на два инстанса одновременно",
                List.of("request:" + key, "flight:req-A", "flight:req-B"), state());

        if (guard == Guard.CHECK_THEN_INSERT) {
            // Both instances run their SELECT before either runs its INSERT.
            // That gap is the whole bug: the check is not the write.
            Entry seenByA = lookup(dedupKeyFor(key, item, amount));
            phase("req-A", seenByA == null ? "check passed" : "duplicate");
            Trace.event("DEDUP_CHECK",
                    "req-A: SELECT ... WHERE dedup_key = '" + dedupKeyFor(key, item, amount)
                            + "' -> " + (seenByA == null ? "no row, looks new" : "found"),
                    "req-A: SELECT ... WHERE dedup_key = '" + dedupKeyFor(key, item, amount)
                            + "' -> " + (seenByA == null ? "строк нет, выглядит новой" : "найдено"),
                    List.of("flight:req-A"), state());

            Entry seenByB = lookup(dedupKeyFor(key, item, amount));
            phase("req-B", seenByB == null ? "check passed" : "duplicate");
            Trace.event("DEDUP_CHECK",
                    "req-B: the same SELECT -> " + (seenByB == null ? "no row either" : "found")
                            + ". Both requests passed the check before either wrote anything",
                    "req-B: тот же SELECT -> " + (seenByB == null ? "строк тоже нет" : "найдено")
                            + ". Оба запроса прошли проверку до того, как хоть один что-то записал",
                    List.of("flight:req-B"), state());

            settle("req-A", seenByA, key, item, amount);
            settle("req-B", seenByB, key, item, amount);
            return;
        }

        apply(key, item, amount, "req-A");
        apply(key, item, amount, "req-B");
    }

    /**
     * The atomicity trap: the dedup row is committed in its own transaction and
     * the process dies before the sale row is inserted. The key now exists
     * without a sale, so the retry is deduplicated against nothing.
     */
    public void receiveSplitCommit(String key, String item, int amount) {
        inFlight.clear();
        startRequest(key, item, amount);
        Trace.event("REQUEST_RECEIVED",
                "POST /sales arrives with key '" + key + "' (" + item + ", " + amount + ")",
                "Приходит POST /sales с ключом '" + key + "' (" + item + ", " + amount + ")",
                List.of("request:" + key), state());

        index.add(new Entry(dedupKeyFor(key, item, amount), key, item, amount, CREATED, day));
        request.outcome = "crashed";
        Trace.event("CRASH_BETWEEN_WRITES",
                "Transaction 1 committed the dedup row for '" + key + "'; the process died "
                        + "before transaction 2 inserted the sale — the key exists, the sale does not",
                "Транзакция 1 зафиксировала строку дедупликации для '" + key + "'; процесс упал "
                        + "до того, как транзакция 2 вставила продажу — ключ есть, продажи нет",
                List.of("request:" + key, "entry:" + dedupKeyFor(key, item, amount)), state());
    }

    /** Moves the server clock forward, so the retention window can expire keys. */
    public void advanceDays(int days) {
        day += days;
        Trace.event("TIME_PASSED",
                "The server clock moves on: it is now day " + day,
                "Часы сервера идут дальше: сейчас день " + day,
                List.of(), state());
    }

    /**
     * The retention job: deletes dedup keys older than the window. Anything
     * retried after its key was purged is invisible to the guard again.
     */
    public void purgeDedupKeys(int retentionDays) {
        this.retentionDays = retentionDays;
        int before = index.size();
        index.removeIf(entry -> day - entry.day >= retentionDays);
        int removed = before - index.size();
        Trace.event("KEYS_PURGED",
                "Retention job: dedup keys older than " + retentionDays + " day(s) are deleted — "
                        + removed + " removed, " + index.size() + " left. A retry older than the "
                        + "window is invisible to the guard again",
                "Задача очистки: ключи дедупликации старше " + retentionDays + " дн. удалены — "
                        + "удалено " + removed + ", осталось " + index.size() + ". Повтор старше "
                        + "окна снова невидим для защиты",
                List.of(), state());
    }

    /** Prints the audit an accountant would run at the end of the day. */
    public void report() {
        Trace.event("LEDGER_AUDIT",
                "Audit: " + sales.size() + " sale row(s) totalling " + total
                        + "; blocked retries: " + blocked + ", duplicate rows: " + duplicates
                        + ", silently lost sales: " + lost,
                "Сверка: строк продаж — " + sales.size() + ", на сумму " + total
                        + "; заблокировано повторов: " + blocked + ", строк-дублей: " + duplicates
                        + ", молча потеряно продаж: " + lost,
                List.of(), state());
    }

    // ---------------------------------------------------------------- internals

    /** Runs one request through the configured guard. */
    private boolean apply(String key, String item, int amount, String flightId) {
        String dedupKey = dedupKeyFor(key, item, amount);
        Entry found = lookup(dedupKey);

        switch (guard) {
            case CHECK_THEN_INSERT:
                Trace.event("DEDUP_CHECK",
                        flightId + ": SELECT ... WHERE dedup_key = '" + dedupKey + "' -> "
                                + (found == null ? "no row, looks new" : "found"),
                        flightId + ": SELECT ... WHERE dedup_key = '" + dedupKey + "' -> "
                                + (found == null ? "строк нет, выглядит новой" : "найдено"),
                        List.of("entry:" + dedupKey), state());
                break;
            case UNIQUE_INDEX:
                if (found != null) {
                    Trace.event("CONSTRAINT_VIOLATION",
                            "The INSERT hit the UNIQUE index on dedup_key '" + dedupKey
                                    + "' — the database refused the second row, so the server "
                                    + "loads the stored record instead of writing one",
                            "INSERT наткнулся на UNIQUE-индекс по dedup_key '" + dedupKey
                                    + "' — база отказала во второй строке, поэтому сервер читает "
                                    + "сохранённую запись вместо записи новой",
                            List.of("entry:" + dedupKey), state());
                }
                break;
            case UPSERT:
                // INSERT ... ON CONFLICT DO NOTHING: no exception, 0 rows affected.
                break;
            default:
                break;
        }

        return settle(flightId, found, key, item, amount);
    }

    /** Applies the decision once the guard has been consulted. */
    private boolean settle(String flightId, Entry found, String key, String item, int amount) {
        if (found == null) {
            return insert(key, item, amount, dedupKeyFor(key, item, amount),
                    guard != Guard.NONE, flightId);
        }
        block(found, key, item, amount, flightId);
        return false;
    }

    /** Writes a sale row (and, for a real guard, the dedup row that protects it). */
    private boolean insert(String key, String item, int amount, String dedupKey,
                           boolean writeIndex, String flightId) {
        boolean repeat = salesHaveKey(key);
        sales.add(new Row(sales.size() + 1, key, item, amount));
        total += amount;
        if (writeIndex) {
            index.add(new Entry(dedupKey, key, item, amount, CREATED, day));
        }
        phase(flightId, repeat ? "duplicate" : "inserted");

        if (repeat) {
            duplicates++;
            request.outcome = "duplicated";
            Trace.event("DUPLICATE_REGISTERED",
                    "A SECOND sale row for '" + key + "' (" + item + ", " + amount
                            + ") is now in the table — the same business action was registered "
                            + "twice and the total is " + total,
                    "В таблице теперь ВТОРАЯ строка продажи для '" + key + "' (" + item + ", "
                            + amount + ") — одно и то же бизнес-действие зарегистрировано дважды, "
                            + "итог " + total,
                    List.of("request:" + key, "row:" + sales.size()), state());
            return true;
        }

        registered++;
        request.outcome = "registered";
        Trace.event("SALE_REGISTERED",
                "New dedup key '" + dedupKey + "': the sale (" + item + ", " + amount
                        + ") and its stored response are written in ONE transaction — total is "
                        + total,
                "Новый ключ дедупликации '" + dedupKey + "': продажа (" + item + ", " + amount
                        + ") и её сохранённый ответ пишутся в ОДНОЙ транзакции — итог " + total,
                List.of("request:" + key, "row:" + sales.size(), "entry:" + dedupKey), state());
        return true;
    }

    /** The guard recognised the dedup key — decide what that actually means. */
    private void block(Entry found, String key, String item, int amount, String flightId) {
        if (dedupByContent && !found.sourceKey.equals(key)) {
            lost++;
            phase(flightId, "rejected");
            request.outcome = "lost";
            Trace.event("GENUINE_SALE_LOST",
                    "Sale '" + key + "' (" + item + ", " + amount + ") hashes to the same dedup "
                            + "key as '" + found.sourceKey + "', so it is rejected as a duplicate "
                            + "— but it is a different customer's real sale, and it is now gone",
                    "Продажа '" + key + "' (" + item + ", " + amount + ") даёт тот же ключ "
                            + "дедупликации, что и '" + found.sourceKey + "', поэтому отклонена как "
                            + "дубль — но это настоящая продажа другого покупателя, и она потеряна",
                    List.of("request:" + key, "entry:" + found.dedupKey), state());
            return;
        }

        if (!dedupByContent && (!found.item.equals(item) || found.amount != amount)) {
            phase(flightId, "conflict");
            request.outcome = "conflict";
            Trace.event("KEY_CONFLICT",
                    "Key '" + key + "' is known but the body differs: stored (" + found.item + ", "
                            + found.amount + ") vs incoming (" + item + ", " + amount
                            + ") — the server answers 409 Conflict instead of silently replaying "
                            + "the old response",
                    "Ключ '" + key + "' известен, но тело отличается: сохранено (" + found.item
                            + ", " + found.amount + ") против пришедшего (" + item + ", " + amount
                            + ") — сервер отвечает 409 Conflict, а не молча повторяет старый ответ",
                    List.of("request:" + key, "entry:" + found.dedupKey), state());
            return;
        }

        blocked++;
        phase(flightId, "deduplicated");
        request.outcome = "blocked";
        Trace.event("DUPLICATE_BLOCKED",
                "Key '" + key + "' is already in the dedup index — nothing is applied and the "
                        + "stored response (" + found.response + ") is replayed; the table still "
                        + "holds " + sales.size() + " row(s), total " + total,
                "Ключ '" + key + "' уже есть в индексе дедупликации — ничего не применяется, "
                        + "возвращается сохранённый ответ (" + found.response + "); в таблице "
                        + "по-прежнему строк: " + sales.size() + ", итог " + total,
                List.of("request:" + key, "entry:" + found.dedupKey), state());

        if (!salesHaveKey(key)) {
            lost++;
            request.outcome = "lost";
            Trace.event("SALE_SILENTLY_LOST",
                    "There is no sale row for '" + key + "' at all: the dedup key was committed "
                            + "without it, so every retry is answered 'already done' and the sale "
                            + "can never be registered",
                    "Строки продажи для '" + key + "' вообще нет: ключ дедупликации был "
                            + "зафиксирован без неё, поэтому на любой повтор отвечают «уже сделано», "
                            + "и продажу уже не зарегистрировать",
                    List.of("request:" + key), state());
        }
    }

    private void startRequest(String key, String item, int amount) {
        request = new Request(key, item, amount, "pending");
    }

    private String dedupKeyFor(String key, String item, int amount) {
        return dedupByContent ? "hash(" + item + ":" + amount + ")" : key;
    }

    private Entry lookup(String dedupKey) {
        for (Entry entry : index) {
            if (entry.dedupKey.equals(dedupKey)) {
                return entry;
            }
        }
        return null;
    }

    private boolean salesHaveKey(String key) {
        for (Row row : sales) {
            if (row.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void phase(String flightId, String phase) {
        for (Flight flight : inFlight) {
            if (flight.id.equals(flightId)) {
                flight.phase = phase;
            }
        }
    }

    private String guardCode() {
        switch (guard) {
            case CHECK_THEN_INSERT:
                return "check-then-insert";
            case UNIQUE_INDEX:
                return "unique-index";
            case UPSERT:
                return "upsert";
            default:
                return "none";
        }
    }

    private String guardEn() {
        String base;
        switch (guard) {
            case CHECK_THEN_INSERT:
                base = "the application SELECTs, then INSERTs";
                break;
            case UNIQUE_INDEX:
                base = "a UNIQUE index on dedup_key";
                break;
            case UPSERT:
                base = "INSERT ... ON CONFLICT (dedup_key) DO NOTHING";
                break;
            default:
                base = "none";
                break;
        }
        return dedupByContent ? base + ", dedup key = a hash of the payload" : base;
    }

    private String guardRu() {
        String base;
        switch (guard) {
            case CHECK_THEN_INSERT:
                base = "приложение делает SELECT, затем INSERT";
                break;
            case UNIQUE_INDEX:
                base = "UNIQUE-индекс по dedup_key";
                break;
            case UPSERT:
                base = "INSERT ... ON CONFLICT (dedup_key) DO NOTHING";
                break;
            default:
                base = "нет";
                break;
        }
        return dedupByContent ? base + ", ключ дедупликации — хэш тела запроса" : base;
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("guard", guardCode());
        s.put("dedupBy", dedupByContent ? "content-hash" : "idempotency-key");
        s.put("day", day);
        s.put("retentionDays", retentionDays);

        if (request != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", request.key);
            m.put("item", request.item);
            m.put("amount", request.amount);
            m.put("outcome", request.outcome);
            s.put("request", m);
        }

        List<Object> flights = new ArrayList<>();
        for (Flight flight : inFlight) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", flight.id);
            m.put("key", flight.key);
            m.put("phase", flight.phase);
            flights.add(m);
        }
        s.put("inFlight", flights);

        List<Object> entries = new ArrayList<>();
        for (Entry entry : index) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dedupKey", entry.dedupKey);
            m.put("sourceKey", entry.sourceKey);
            m.put("response", entry.response);
            m.put("day", entry.day);
            entries.add(m);
        }
        s.put("dedupIndex", entries);

        List<Object> rows = new ArrayList<>();
        for (Row row : sales) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("row", row.row);
            m.put("key", row.key);
            m.put("item", row.item);
            m.put("amount", row.amount);
            rows.add(m);
        }
        s.put("sales", rows);

        s.put("total", total);
        s.put("registered", registered);
        s.put("blocked", blocked);
        s.put("duplicates", duplicates);
        s.put("lost", lost);
        return s;
    }

    private static final class Request {
        final String key;
        final String item;
        final int amount;
        /** pending | registered | duplicated | blocked | conflict | lost | crashed */
        String outcome;

        Request(String key, String item, int amount, String outcome) {
            this.key = key;
            this.item = item;
            this.amount = amount;
            this.outcome = outcome;
        }
    }

    private static final class Entry {
        final String dedupKey;
        final String sourceKey;
        final String item;
        final int amount;
        final String response;
        final int day;

        Entry(String dedupKey, String sourceKey, String item, int amount, String response, int day) {
            this.dedupKey = dedupKey;
            this.sourceKey = sourceKey;
            this.item = item;
            this.amount = amount;
            this.response = response;
            this.day = day;
        }
    }

    private static final class Row {
        final int row;
        final String key;
        final String item;
        final int amount;

        Row(int row, String key, String item, int amount) {
            this.row = row;
            this.key = key;
            this.item = item;
            this.amount = amount;
        }
    }

    private static final class Flight {
        final String id;
        final String key;
        String phase;

        Flight(String id, String key, String phase) {
            this.id = id;
            this.key = key;
            this.phase = phase;
        }
    }
}
