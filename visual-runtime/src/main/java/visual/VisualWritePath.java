package visual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic teaching model of the PostgreSQL write path for a service whose
 * rows carry a very large payload.
 *
 * <p><strong>This is a cost model, not a benchmark.</strong> Nothing here talks to
 * a database. Every number is produced from the fixed unit costs published as the
 * {@code *_US} / {@code *_BYTES} constants below: a round trip always costs the
 * same, a megabyte of WAL always costs the same. Real numbers depend on hardware,
 * and the point is not to predict them — it is to show <em>which term of the sum
 * dominates</em>, because for a ~30 MB row that term is not the one most answers
 * reach for first.
 *
 * <p>A write is broken into eight parts, and every part is visible in the trace
 * state so the visualizer can draw the breakdown:
 *
 * <ul>
 *   <li><b>network</b> — one client/server round trip per statement;</li>
 *   <li><b>parse</b> — server-side parse/plan/execute of one statement;</li>
 *   <li><b>compress</b> — the TOAST compression attempt on an oversized value;</li>
 *   <li><b>table</b> — writing the heap and TOAST pages;</li>
 *   <li><b>toast</b> — the per-chunk overhead of slicing the value into ~2 KB rows;</li>
 *   <li><b>wal</b> — writing the same bytes again to the write-ahead log;</li>
 *   <li><b>index</b> — one entry per index per row;</li>
 *   <li><b>commit</b> — waiting (or not) for the WAL flush.</li>
 * </ul>
 *
 * <p>Configuration is fluent and each change re-prices one row, so the effect of
 * a decision is visible before any row is written. {@link #insertRowByRow},
 * {@link #insertBatch} and {@link #copyIn} then write rows with the three usual
 * strategies, and {@link #serveApi} converts the per-write cost into what the
 * connection pool can let through when many services call the API at once.
 *
 * <p>The model is intentionally dependency-free.
 */
public class VisualWritePath {

    private static final long MB = 1024L * 1024L;

    // --- unit costs -----------------------------------------------------------
    // Fixed by construction so a run is reproducible. Orders of magnitude are
    // realistic for a networked database on an SSD; exact values are not claims.

    /** One client/server round trip: network there and back. */
    private static final long ROUND_TRIP_US = 400;
    /** Server-side parse/plan/execute of one INSERT statement. */
    private static final long STATEMENT_US = 60;
    /** Per-row cost inside a COPY stream: no per-row parse or plan. */
    private static final long COPY_ROW_US = 6;
    /** A commit that waits for the WAL to reach the disk. */
    private static final long COMMIT_FLUSH_US = 1500;
    /** A commit that returns before the WAL is flushed (synchronous_commit = off). */
    private static final long COMMIT_ASYNC_US = 20;
    /** Writing one megabyte of heap/TOAST pages. */
    private static final long IO_US_PER_MB = 1200;
    /** Writing one megabyte of WAL. */
    private static final long WAL_US_PER_MB = 1200;
    /** Attempting to compress one megabyte before TOASTing it. */
    private static final long COMPRESS_US_PER_MB = 900;
    /** Bookkeeping for one TOAST chunk row plus its index entry. */
    private static final long TOAST_CHUNK_US = 3;
    /** Inserting one entry into one B-tree index. */
    private static final long INDEX_ENTRY_US = 60;
    /** Sustained write bandwidth of the volume holding the table and the WAL. */
    private static final long DISK_BYTES_PER_SEC = 500 * MB;

    /** Values larger than this are compressed and moved out of line. */
    private static final long TOAST_THRESHOLD_BYTES = 2000;
    /** Size of one slice of an out-of-line value. */
    private static final long TOAST_CHUNK_BYTES = 2000;
    /** WAL record overhead for the main row itself. */
    private static final long WAL_ROW_OVERHEAD_BYTES = 200;
    /** WAL record overhead per TOAST chunk. */
    private static final long WAL_PER_CHUNK_BYTES = 64;
    /** WAL record overhead per index entry. */
    private static final long WAL_PER_INDEX_ENTRY_BYTES = 64;
    /** What the TOAST compressor gets back on compressible data. */
    private static final long COMPRESSIBLE_RATIO = 4;

    /** Rows written one by one before the trace folds the rest into one event. */
    private static final int MAX_DETAIL_ROWS = 4;

    // --- configuration --------------------------------------------------------

    private final String table;
    private long payloadBytes = 30 * MB;
    private boolean compressible;
    private boolean columnCompression = true;
    private boolean offloaded;
    private long metadataBytes;
    private int secondaryIndexes;
    private boolean flushOnCommit = true;
    private int poolSize = 20;

    // --- observed work --------------------------------------------------------

    private LastWrite lastWrite;
    private Pool pool;

    private long totalRows;
    private long totalRoundTrips;
    private long totalCommits;
    private long totalFlushes;
    private long totalWalBytes;
    private long totalToastChunks;
    private long totalIndexEntries;
    private long totalUs;

    private VisualWritePath(String table) {
        this.table = Objects.requireNonNull(table, "table");
        Trace.event("WRITER_CONFIGURED",
                "Table '" + table + "': one row carries a " + bytes(payloadBytes)
                        + " payload in a bytea column, no secondary indexes yet. All timings below "
                        + "come from a fixed cost model, not from a real server — " + costEn(),
                "Таблица '" + table + "': одна строка несёт полезную нагрузку " + bytes(payloadBytes)
                        + " в колонке bytea, вторичных индексов пока нет. Все тайминги ниже берутся "
                        + "из фиксированной модели стоимости, а не с реального сервера — " + costRu(),
                List.of("payload"), state());
    }

    /** Starts a scene for one write-only table. */
    public static VisualWritePath table(String name) {
        return new VisualWritePath(name);
    }

    // --- configuration knobs --------------------------------------------------

    /**
     * Sets how big one record's payload is and whether it compresses.
     *
     * @param megabytes   payload size per row
     * @param compressible true for text/JSON/XML, false for data that is already
     *                     compressed (JPEG, PDF, a zip, an encrypted blob)
     */
    public VisualWritePath payload(int megabytes, boolean compressible) {
        require(megabytes > 0, "megabytes must be positive");
        this.payloadBytes = (long) megabytes * MB;
        this.compressible = compressible;
        Trace.event("WRITER_CONFIGURED",
                "Payload per row: " + bytes(payloadBytes) + ", "
                        + (compressible ? "compressible (text/JSON)" : "already compressed (binary)")
                        + " — " + costEn(),
                "Полезная нагрузка на строку: " + bytes(payloadBytes) + ", "
                        + (compressible ? "сжимаемая (текст/JSON)" : "уже сжатая (бинарная)")
                        + " — " + costRu(),
                List.of("payload"), state());
        return this;
    }

    /** Adds N secondary indexes to the table; every insert must maintain all of them. */
    public VisualWritePath secondaryIndexes(int count) {
        require(count >= 0, "count must not be negative");
        this.secondaryIndexes = count;
        Trace.event("WRITER_CONFIGURED",
                "The table now has " + count + " secondary index(es), so every INSERT writes "
                        + (count + 1) + " index entry(ies) — " + costEn(),
                "У таблицы теперь вторичных индексов: " + count + ", поэтому каждый INSERT пишет "
                        + "записей в индексы: " + (count + 1) + " — " + costRu(),
                List.of("index"), state());
        return this;
    }

    /**
     * {@code ALTER TABLE ... ALTER COLUMN payload SET STORAGE EXTERNAL}: keep the
     * value out of line but stop trying to compress it. Free speed for data that
     * is already compressed, and a waste of space for data that is not.
     */
    public VisualWritePath externalStorage() {
        this.columnCompression = false;
        Trace.event("WRITER_CONFIGURED",
                "SET STORAGE EXTERNAL: the payload is still stored out of line, but PostgreSQL no "
                        + "longer attempts to compress it first — " + costEn(),
                "SET STORAGE EXTERNAL: нагрузка по-прежнему хранится вне строки, но PostgreSQL "
                        + "больше не пытается сначала её сжать — " + costRu(),
                List.of("payload"), state());
        return this;
    }

    /**
     * The design change: the payload goes to object storage and the row keeps only
     * a reference to it (URL, checksum, size, timestamps).
     *
     * @param metadataRowBytes size of the metadata row that stays in PostgreSQL
     */
    public VisualWritePath offloadPayload(int metadataRowBytes) {
        require(metadataRowBytes > 0, "metadataRowBytes must be positive");
        long before = oneRequestUs();
        this.offloaded = true;
        this.metadataBytes = metadataRowBytes;
        long after = oneRequestUs();
        Trace.event("PAYLOAD_OFFLOADED",
                "The " + bytes(payloadBytes) + " payload now goes to object storage; PostgreSQL keeps "
                        + "a " + bytes(metadataBytes) + " row with the URL, the checksum and the size. "
                        + "One write drops from " + ms(before) + " to " + ms(after) + " — "
                        + speedup(before, after) + " faster, and the database stops being a file server",
                "Нагрузка " + bytes(payloadBytes) + " теперь уходит в объектное хранилище; PostgreSQL "
                        + "хранит строку " + bytes(metadataBytes) + " с URL, контрольной суммой и "
                        + "размером. Одна запись падает с " + ms(before) + " до " + ms(after) + " — "
                        + "в " + speedup(before, after) + " быстрее, и база перестаёт быть файловым "
                        + "сервером",
                List.of("payload", "row"), state());
        return this;
    }

    /**
     * {@code synchronous_commit = off}: a commit returns before its WAL record is
     * on disk. Trades a window of recently committed transactions on a crash — not
     * corruption, and not the isolation guarantees.
     */
    public VisualWritePath asyncCommit() {
        this.flushOnCommit = false;
        Trace.event("WRITER_CONFIGURED",
                "synchronous_commit = off: a commit no longer waits for the WAL flush ("
                        + ms(COMMIT_FLUSH_US) + " -> " + ms(COMMIT_ASYNC_US) + " per commit). "
                        + "A crash can lose the last few committed transactions; the database stays "
                        + "consistent — " + costEn(),
                "synchronous_commit = off: коммит больше не ждёт сброса WAL на диск ("
                        + ms(COMMIT_FLUSH_US) + " -> " + ms(COMMIT_ASYNC_US) + " на коммит). "
                        + "При падении можно потерять несколько последних зафиксированных транзакций; "
                        + "база остаётся согласованной — " + costRu(),
                List.of("commit"), state());
        return this;
    }

    /** Sets how many database connections the whole service shares. */
    public VisualWritePath connectionPool(int size) {
        require(size > 0, "size must be positive");
        this.poolSize = size;
        Trace.event("WRITER_CONFIGURED",
                "The service shares a pool of " + size + " connection(s) between every caller of its "
                        + "API — " + costEn(),
                "Сервис делит пул из " + size + " соединений между всеми вызывающими его API — "
                        + costRu(),
                List.of("pool"), state());
        return this;
    }

    // --- where the cost is ----------------------------------------------------

    /** Shows what PostgreSQL does with a value that does not fit in a page. */
    public void explainPayload() {
        RowCost c = rowCost();
        if (c.toastChunks == 0) {
            Trace.event("INLINE_PAYLOAD",
                    "The row is " + bytes(c.rowBytes) + ", below the ~2 KB TOAST threshold, so it stays "
                            + "inline in its heap page: no compression attempt, no chunking, no TOAST "
                            + "index. It writes " + bytes(c.walBytes) + " of WAL, so the bytes have left "
                            + "the picture entirely — what is left of the "
                            + ms(oneRequestUs()) + " write is the round trip, the index entries and the "
                            + "commit",
                    "Строка занимает " + bytes(c.rowBytes) + ", это ниже порога TOAST (~2 КБ), поэтому "
                            + "она остаётся внутри своей heap-страницы: ни попытки сжатия, ни нарезки на "
                            + "куски, ни TOAST-индекса. Она пишет " + bytes(c.walBytes)
                            + " WAL, то есть байты полностью ушли из картины — от записи в "
                            + ms(oneRequestUs()) + " остались round trip, записи в индексы и коммит",
                    List.of("payload", "row"), state());
            return;
        }
        String compressEn = !columnCompression
                ? "the column is SET STORAGE EXTERNAL, so the compression attempt is skipped"
                : compressible
                ? "compression shrinks it to " + bytes(c.storedBytes) + " for " + ms(c.compressUs)
                + " of CPU"
                : "compression is attempted and gives nothing back — the bytes are already compressed, "
                + "and the attempt still costs " + ms(c.compressUs);
        String compressRu = !columnCompression
                ? "колонка объявлена SET STORAGE EXTERNAL, поэтому попытка сжатия пропускается"
                : compressible
                ? "сжатие уменьшает её до " + bytes(c.storedBytes) + " за " + ms(c.compressUs)
                + " процессорного времени"
                : "сжатие пробуется и не даёт ничего — байты уже сжаты, но попытка всё равно стоит "
                + ms(c.compressUs);
        Trace.event("TOAST_SPILL",
                "A " + bytes(c.rowBytes) + " value cannot live in an 8 KB page: " + compressEn
                        + ", and the remaining " + bytes(c.storedBytes) + " is stored out of line as "
                        + c.toastChunks + " TOAST chunk rows, each with its own index entry. One INSERT "
                        + "is really " + (c.toastChunks + 1) + " row writes",
                "Значение " + bytes(c.rowBytes) + " не помещается в страницу 8 КБ: " + compressRu
                        + ", а оставшиеся " + bytes(c.storedBytes) + " хранятся вне строки как "
                        + c.toastChunks + " строк-кусков TOAST, у каждой своя запись в индексе. Один "
                        + "INSERT на деле является записью строк: " + (c.toastChunks + 1),
                List.of("payload", "toast"), state());
    }

    /** Shows how many bytes reach the disk for every byte of payload. */
    public void explainWal() {
        RowCost c = rowCost();
        long disk = c.storedBytes + c.walBytes;
        Trace.event("WAL_AMPLIFIED",
                "Every row is written twice: " + bytes(c.storedBytes) + " into the table and "
                        + bytes(c.walBytes) + " into the WAL, so " + bytes(c.rowBytes)
                        + " of payload costs " + bytes(disk) + " of disk. Each streaming replica and "
                        + "the WAL archive copy those " + bytes(c.walBytes) + " again",
                "Каждая строка пишется дважды: " + bytes(c.storedBytes) + " в таблицу и "
                        + bytes(c.walBytes) + " в WAL, поэтому " + bytes(c.rowBytes)
                        + " полезных данных обходятся в " + bytes(disk) + " на диске. Каждая потоковая "
                        + "реплика и архив WAL копируют эти " + bytes(c.walBytes) + " ещё раз",
                List.of("wal"), state());
    }

    /** Shows what index maintenance costs against the rest of the write. */
    public void explainIndexes() {
        RowCost c = rowCost();
        long request = oneRequestUs();
        Trace.event("INDEX_MAINTENANCE",
                "Each row writes " + c.indexEntries + " index entry(ies) (1 primary key + "
                        + secondaryIndexes + " secondary): " + ms(c.indexUs) + " and "
                        + bytes(c.indexEntries * WAL_PER_INDEX_ENTRY_BYTES) + " of extra WAL. Against a "
                        + ms(request) + " write that is " + percent(c.indexUs, request)
                        + " — " + indexVerdictEn(c.indexUs, request),
                "Каждая строка пишет записей в индексы: " + c.indexEntries + " (1 первичный ключ + "
                        + secondaryIndexes + " вторичных): " + ms(c.indexUs) + " и "
                        + bytes(c.indexEntries * WAL_PER_INDEX_ENTRY_BYTES) + " дополнительного WAL. На "
                        + "фоне записи в " + ms(request) + " это " + percent(c.indexUs, request)
                        + " — " + indexVerdictRu(c.indexUs, request),
                List.of("index"), state());
    }

    // --- writes ---------------------------------------------------------------

    /**
     * The default JPA/JDBC shape: one INSERT per record, each in its own
     * transaction. One round trip and one commit per row.
     */
    public void insertRowByRow(int rows) {
        require(rows > 0, "rows must be positive");
        RowCost c = rowCost();
        long perRow = oneRequestUs();
        lastWrite = new LastWrite("row-by-row", rows, rows, rows, rows,
                flushOnCommit ? rows : 0, rows * c.walBytes, rows * perRow);

        int detailed = Math.min(rows, MAX_DETAIL_ROWS);
        for (int i = 1; i <= detailed; i++) {
            accrue(c, 1, 1, 1, perRow);
            Trace.event("ROW_INSERTED",
                    "INSERT #" + i + ": one round trip, " + bytes(c.storedBytes) + " to the table, "
                            + bytes(c.walBytes) + " to the WAL, " + c.indexEntries
                            + " index entry(ies), then its own commit — " + ms(perRow),
                    "INSERT #" + i + ": один round trip, " + bytes(c.storedBytes) + " в таблицу, "
                            + bytes(c.walBytes) + " в WAL, записей в индексы: " + c.indexEntries
                            + ", затем собственный коммит — " + ms(perRow),
                    List.of("row", "wal"), state());
        }
        if (rows > detailed) {
            int rest = rows - detailed;
            accrue(c, rest, rest, rest, rest * perRow);
            Trace.event("ROWS_INSERTED",
                    "Rows " + (detailed + 1) + "-" + rows + " go the same way: " + rest
                            + " more round trips, " + rest + " more commits, "
                            + bytes(rest * c.walBytes) + " more WAL",
                    "Строки " + (detailed + 1) + "-" + rows + " идут так же: ещё round trips: " + rest
                            + ", ещё коммитов: " + rest + ", ещё WAL: " + bytes(rest * c.walBytes),
                    List.of("row", "wal"), state());
        }
        commitEvent(rows, rows * perRow);
    }

    /** All rows in one transaction, sent as one batch. */
    public void insertBatch(int rows) {
        insertBatch(rows, rows);
    }

    /**
     * JDBC batching: {@code addBatch()} N times, {@code executeBatch()} once per
     * batch, one commit per batch. Collapses round trips and commits; the bytes
     * are untouched.
     */
    public void insertBatch(int rows, int batchSize) {
        require(rows > 0, "rows must be positive");
        require(batchSize > 0, "batchSize must be positive");
        RowCost c = rowCost();
        long batches = ceilDiv(rows, batchSize);
        long us = batches * ROUND_TRIP_US + rows * STATEMENT_US + rows * c.us
                + batches * commitUs();
        long rowByRowUs = rows * oneRequestUs();
        lastWrite = new LastWrite("batch", rows, batches, batches, batches,
                flushOnCommit ? batches : 0, rows * c.walBytes, us);
        accrue(c, rows, batches, batches, us);

        long saved = rowByRowUs - us;
        Trace.event("BATCH_EXECUTED",
                rows + " row(s) sent as " + batches + " batch(es) inside " + batches
                        + " transaction(s): " + batches + " round trip(s) instead of " + rows + " and "
                        + batches + " commit(s) instead of " + rows + ". " + ms(rowByRowUs) + " -> "
                        + ms(us) + ", saving " + ms(saved) + " (" + percent(saved, rowByRowUs)
                        + "). Batching removes the per-statement and per-commit overhead and nothing "
                        + "else, so it wins exactly as much as that overhead was worth",
                "Строк отправлено: " + rows + " как батчей: " + batches + " внутри транзакций: "
                        + batches + ": round trips: " + batches + " вместо " + rows + " и коммитов: "
                        + batches + " вместо " + rows + ". " + ms(rowByRowUs) + " -> " + ms(us)
                        + ", экономия " + ms(saved) + " (" + percent(saved, rowByRowUs)
                        + "). Батчинг убирает накладные расходы на каждый statement и каждый коммит и "
                        + "ничего больше, поэтому выигрыш ровно такой, каким был этот overhead",
                List.of("row", "commit"), state());
        commitEvent(batches, us);
    }

    /**
     * {@code COPY ... FROM STDIN}: the rows are streamed in one statement, with no
     * per-row parse or plan. The bulk-load path.
     */
    public void copyIn(int rows) {
        require(rows > 0, "rows must be positive");
        RowCost c = rowCost();
        long us = ROUND_TRIP_US + rows * COPY_ROW_US + rows * c.us + commitUs();
        long batchUs = ROUND_TRIP_US + rows * STATEMENT_US + rows * c.us + commitUs();
        lastWrite = new LastWrite("copy", rows, 1, 1, 1, flushOnCommit ? 1 : 0,
                rows * c.walBytes, us);
        accrue(c, rows, 1, 1, us);

        Trace.event("COPY_STREAMED",
                "COPY " + table + " FROM STDIN streamed " + rows + " row(s) in one statement: no "
                        + "per-row parse or plan, " + ms(COPY_ROW_US) + " per row instead of "
                        + ms(STATEMENT_US) + ". " + ms(batchUs) + " batched -> " + ms(us)
                        + " with COPY. The table work below it is identical",
                "COPY " + table + " FROM STDIN передал строк: " + rows + " одним statement: без "
                        + "разбора и планирования на каждую строку, " + ms(COPY_ROW_US)
                        + " на строку вместо " + ms(STATEMENT_US) + ". " + ms(batchUs)
                        + " батчем -> " + ms(us) + " через COPY. Работа с таблицей под этим — та же",
                List.of("row"), state());
        commitEvent(1, us);
    }

    // --- the API side ---------------------------------------------------------

    /**
     * Many services call this API at once, each submitting one record. Converts the
     * per-write cost into what the shared connection pool can let through.
     */
    public void serveApi(int concurrentCallers) {
        require(concurrentCallers > 0, "concurrentCallers must be positive");
        RowCost c = rowCost();
        long request = oneRequestUs();
        long inUse = Math.min(concurrentCallers, poolSize);
        long queued = Math.max(0, concurrentCallers - poolSize);

        // Two independent ceilings: how many writes the connections can hold at
        // once, and how many the disk can absorb.
        long poolCeiling = Math.max(1, poolSize * 1_000_000L / Math.max(1, request));
        long bytesPerRow = c.storedBytes + c.walBytes;
        long diskCeiling = Math.max(1, DISK_BYTES_PER_SEC / Math.max(1, bytesPerRow));
        boolean diskBound = diskCeiling < poolCeiling;
        long capacity = Math.min(poolCeiling, diskCeiling);
        long drainUs = Math.max(request, concurrentCallers * 1_000_000L / capacity);
        pool = new Pool(poolSize, concurrentCallers, inUse, queued, drainUs, request,
                capacity, poolCeiling, diskCeiling, diskBound ? "disk" : "pool");

        if (queued == 0) {
            Trace.event("POOL_HEADROOM",
                    concurrentCallers + " caller(s) against " + poolSize + " connection(s): everyone "
                            + "gets a connection immediately and waits only for their own write ("
                            + ms(request) + "). The service absorbs about " + capacity
                            + " write(s)/s, " + boundEn(diskBound, poolCeiling, diskCeiling, bytesPerRow),
                    "Вызывающих: " + concurrentCallers + " на соединений: " + poolSize + ": каждый "
                            + "сразу получает соединение и ждёт только собственную запись ("
                            + ms(request) + "). Сервис принимает примерно " + capacity
                            + " записей/с, " + boundRu(diskBound, poolCeiling, diskCeiling, bytesPerRow),
                    List.of("pool"), state());
            return;
        }
        Trace.event("POOL_SATURATED",
                concurrentCallers + " caller(s) against " + poolSize + " connection(s): " + inUse
                        + " are writing and " + queued + " are queued. The service absorbs about "
                        + capacity + " write(s)/s, " + boundEn(diskBound, poolCeiling, diskCeiling,
                        bytesPerRow) + ". The burst of " + concurrentCallers + " takes " + dur(drainUs)
                        + " to clear",
                "Вызывающих: " + concurrentCallers + " на соединений: " + poolSize + ": пишут: " + inUse
                        + ", в очереди: " + queued + ". Сервис принимает примерно " + capacity
                        + " записей/с, " + boundRu(diskBound, poolCeiling, diskCeiling, bytesPerRow)
                        + ". Всплеск из " + concurrentCallers + " разбирается за " + dur(drainUs),
                List.of("pool"), state());
    }

    private static String boundEn(boolean diskBound, long poolCeiling, long diskCeiling,
                                  long bytesPerRow) {
        return diskBound
                ? "and the limit is the disk, not the pool: " + bytes(bytesPerRow) + " reaches the "
                + "volume per row, so a 500.0 MB/s device tops out at " + diskCeiling
                + " write(s)/s while the connections alone would allow " + poolCeiling
                + ". Adding connections moves the queue, not the ceiling"
                : "and the limit is the connections: the disk could take " + diskCeiling
                + " write(s)/s at " + bytes(bytesPerRow) + " per row, while " + poolCeiling
                + " is all the pool can hold open. Here a bigger pool (or a cheaper write) "
                + "actually helps";
    }

    private static String boundRu(boolean diskBound, long poolCeiling, long diskCeiling,
                                  long bytesPerRow) {
        return diskBound
                ? "и ограничивает здесь диск, а не пул: на строку до тома доходит "
                + bytes(bytesPerRow) + ", поэтому устройство на 500.0 MB/s упирается в "
                + diskCeiling + " записей/с, тогда как одни лишь соединения позволили бы "
                + poolCeiling + ". Добавление соединений двигает очередь, а не потолок"
                : "и ограничивают здесь соединения: диск при " + bytes(bytesPerRow)
                + " на строку принял бы " + diskCeiling + " записей/с, а пул способен держать "
                + "открытыми только " + poolCeiling + ". Вот здесь больший пул (или более "
                + "дешёвая запись) действительно помогает";
    }

    /** Prints everything this scene wrote. */
    public void report() {
        Trace.event("WRITE_REPORT",
                "Total: " + totalRows + " row(s), " + totalRoundTrips + " round trip(s), "
                        + totalCommits + " commit(s) of which " + totalFlushes + " waited for an fsync, "
                        + bytes(totalWalBytes) + " of WAL, " + totalToastChunks + " TOAST chunk(s), "
                        + totalIndexEntries + " index entry(ies), " + ms(totalUs) + " of modelled time",
                "Итого: строк: " + totalRows + ", round trips: " + totalRoundTrips + ", коммитов: "
                        + totalCommits + ", из них ждали fsync: " + totalFlushes + ", WAL: "
                        + bytes(totalWalBytes) + ", кусков TOAST: " + totalToastChunks
                        + ", записей в индексы: " + totalIndexEntries + ", смоделированное время: "
                        + ms(totalUs),
                List.of(), state());
    }

    // --- internals ------------------------------------------------------------

    private void commitEvent(long commits, long writeUs) {
        long spent = commits * commitUs();
        if (flushOnCommit) {
            Trace.event("COMMIT_FLUSHED",
                    commits + " commit(s), each waiting for the WAL to reach the disk: " + ms(spent)
                            + " of the " + ms(writeUs) + " this write took ("
                            + percent(spent, writeUs) + ")",
                    "Коммитов: " + commits + ", каждый ждёт, пока WAL дойдёт до диска: " + ms(spent)
                            + " из " + ms(writeUs) + ", которые заняла эта запись ("
                            + percent(spent, writeUs) + ")",
                    List.of("commit"), state());
            return;
        }
        Trace.event("COMMIT_UNFLUSHED",
                commits + " commit(s) returned without waiting for the flush: " + ms(spent)
                        + " instead of " + ms(commits * COMMIT_FLUSH_US)
                        + ". The rows are visible and durable only after the next flush",
                "Коммитов: " + commits + ", вернулись, не дожидаясь сброса на диск: " + ms(spent)
                        + " вместо " + ms(commits * COMMIT_FLUSH_US)
                        + ". Строки видимы, но долговечны лишь после следующего сброса",
                List.of("commit"), state());
    }

    private void accrue(RowCost c, long rows, long roundTrips, long commits, long us) {
        totalRows += rows;
        totalRoundTrips += roundTrips;
        totalCommits += commits;
        totalFlushes += flushOnCommit ? commits : 0;
        totalWalBytes += rows * c.walBytes;
        totalToastChunks += rows * c.toastChunks;
        totalIndexEntries += rows * c.indexEntries;
        totalUs += us;
    }

    private long commitUs() {
        return flushOnCommit ? COMMIT_FLUSH_US : COMMIT_ASYNC_US;
    }

    /** What one row-by-row INSERT of one record costs end to end. */
    private long oneRequestUs() {
        return rowCost().us + ROUND_TRIP_US + STATEMENT_US + commitUs();
    }

    /** Prices one row against the current configuration. */
    private RowCost rowCost() {
        RowCost c = new RowCost();
        c.rowBytes = offloaded ? metadataBytes : payloadBytes;

        boolean toasted = c.rowBytes > TOAST_THRESHOLD_BYTES;
        if (toasted && columnCompression) {
            c.compressUs = perMb(c.rowBytes, COMPRESS_US_PER_MB);
            c.storedBytes = compressible ? c.rowBytes / COMPRESSIBLE_RATIO : c.rowBytes;
        } else {
            c.storedBytes = c.rowBytes;
        }
        c.toastChunks = toasted ? ceilDiv(c.storedBytes, TOAST_CHUNK_BYTES) : 0;
        c.indexEntries = 1L + secondaryIndexes;
        c.walBytes = c.storedBytes + WAL_ROW_OVERHEAD_BYTES
                + c.toastChunks * WAL_PER_CHUNK_BYTES
                + c.indexEntries * WAL_PER_INDEX_ENTRY_BYTES;

        c.tableUs = perMb(c.storedBytes, IO_US_PER_MB);
        c.walUs = perMb(c.walBytes, WAL_US_PER_MB);
        c.toastUs = c.toastChunks * TOAST_CHUNK_US;
        c.indexUs = c.indexEntries * INDEX_ENTRY_US;
        c.us = c.compressUs + c.tableUs + c.walUs + c.toastUs + c.indexUs;
        return c;
    }

    private String costEn() {
        return "one write now costs " + ms(oneRequestUs());
    }

    private String costRu() {
        return "одна запись теперь стоит " + ms(oneRequestUs());
    }

    private static String indexVerdictEn(long indexUs, long request) {
        return indexUs * 10 < request
                ? "at this row size the indexes are noise, and dropping them buys nothing"
                : "at this row size index maintenance is a real share of the write, and every index "
                + "you do not need is throughput you are giving away";
    }

    private static String indexVerdictRu(long indexUs, long request) {
        return indexUs * 10 < request
                ? "при таком размере строки индексы являются шумом, и их удаление ничего не даст"
                : "при таком размере строки обслуживание индексов является заметной долей записи, и "
                + "каждый ненужный индекс есть отданная пропускная способность";
    }

    private static long perMb(long bytesValue, long usPerMb) {
        return bytesValue * usPerMb / MB;
    }

    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Formats microseconds as milliseconds without touching the default locale. */
    private static String ms(long us) {
        long frac = us % 1000;
        String pad = frac < 10 ? "00" : frac < 100 ? "0" : "";
        return (us / 1000) + "." + pad + frac + " ms";
    }

    /** Same as {@link #ms(long)}, but switches to seconds once that reads better. */
    private static String dur(long us) {
        return us >= 1_000_000 ? decimal(us / 100_000) + " s" : ms(us);
    }

    /** Formats a byte count without touching the default locale. */
    private static String bytes(long value) {
        if (value >= MB) {
            return decimal(value * 10 / MB) + " MB";
        }
        if (value >= 1024) {
            return decimal(value * 10 / 1024) + " KB";
        }
        return value + " B";
    }

    private static String decimal(long tenths) {
        return (tenths / 10) + "." + (tenths % 10);
    }

    private static String percent(long part, long whole) {
        if (whole <= 0) {
            return "0.0%";
        }
        return decimal(part * 1000 / whole) + "%";
    }

    private static String speedup(long slow, long fast) {
        if (fast <= 0) {
            return "0.0x";
        }
        return decimal(slow * 10 / fast) + "x";
    }

    /** Builds the JSON-serializable snapshot consumed by the visualizer. */
    private Object state() {
        RowCost c = rowCost();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("table", table);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("payloadBytes", payloadBytes);
        config.put("payloadPlacement", offloaded ? "object-storage" : "inline");
        config.put("rowBytes", c.rowBytes);
        config.put("compressible", compressible);
        config.put("columnCompression", columnCompression);
        config.put("secondaryIndexes", secondaryIndexes);
        config.put("commitMode", flushOnCommit ? "flush" : "async");
        config.put("poolSize", poolSize);
        s.put("config", config);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("storedBytes", c.storedBytes);
        row.put("walBytes", c.walBytes);
        row.put("toastChunks", c.toastChunks);
        row.put("indexEntries", c.indexEntries);
        row.put("requestUs", oneRequestUs());
        row.put("parts", parts(c));
        s.put("row", row);

        s.put("lastWrite", lastWrite == null ? null : lastWrite.toMap());
        s.put("pool", pool == null ? null : pool.toMap());

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("rows", totalRows);
        totals.put("roundTrips", totalRoundTrips);
        totals.put("commits", totalCommits);
        totals.put("flushes", totalFlushes);
        totals.put("walBytes", totalWalBytes);
        totals.put("toastChunks", totalToastChunks);
        totals.put("indexEntries", totalIndexEntries);
        totals.put("us", totalUs);
        s.put("totals", totals);
        return s;
    }

    /** The per-request cost split into the parts the visualizer draws. */
    private List<Object> parts(RowCost c) {
        List<Object> list = new ArrayList<>();
        addPart(list, "network", ROUND_TRIP_US);
        addPart(list, "parse", STATEMENT_US);
        addPart(list, "compress", c.compressUs);
        addPart(list, "table", c.tableUs);
        addPart(list, "toast", c.toastUs);
        addPart(list, "wal", c.walUs);
        addPart(list, "index", c.indexUs);
        addPart(list, "commit", commitUs());
        return list;
    }

    private static void addPart(List<Object> list, String name, long us) {
        if (us <= 0) {
            return;
        }
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("name", name);
        part.put("us", us);
        list.add(part);
    }

    /** The cost of writing one row with the current configuration. */
    private static final class RowCost {
        long rowBytes;
        long storedBytes;
        long walBytes;
        long toastChunks;
        long indexEntries;
        long compressUs;
        long tableUs;
        long walUs;
        long toastUs;
        long indexUs;
        long us;
    }

    /** One completed write call. */
    private static final class LastWrite {
        final String strategy;
        final long rows;
        final long batches;
        final long roundTrips;
        final long commits;
        final long flushes;
        final long walBytes;
        final long us;

        LastWrite(String strategy, long rows, long batches, long roundTrips, long commits,
                  long flushes, long walBytes, long us) {
            this.strategy = strategy;
            this.rows = rows;
            this.batches = batches;
            this.roundTrips = roundTrips;
            this.commits = commits;
            this.flushes = flushes;
            this.walBytes = walBytes;
            this.us = us;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("strategy", strategy);
            m.put("rows", rows);
            m.put("batches", batches);
            m.put("roundTrips", roundTrips);
            m.put("commits", commits);
            m.put("flushes", flushes);
            m.put("walBytes", walBytes);
            m.put("us", us);
            m.put("usPerRow", rows == 0 ? 0 : us / rows);
            return m;
        }
    }

    /** The connection pool under a burst of API callers. */
    private static final class Pool {
        final long size;
        final long clients;
        final long inUse;
        final long queued;
        final long drainUs;
        final long requestUs;
        final long capacityPerSec;
        final long poolCeilingPerSec;
        final long diskCeilingPerSec;
        /** Which ceiling is actually binding: "pool" or "disk". */
        final String boundBy;

        Pool(long size, long clients, long inUse, long queued, long drainUs, long requestUs,
             long capacityPerSec, long poolCeilingPerSec, long diskCeilingPerSec, String boundBy) {
            this.size = size;
            this.clients = clients;
            this.inUse = inUse;
            this.queued = queued;
            this.drainUs = drainUs;
            this.requestUs = requestUs;
            this.capacityPerSec = capacityPerSec;
            this.poolCeilingPerSec = poolCeilingPerSec;
            this.diskCeilingPerSec = diskCeilingPerSec;
            this.boundBy = boundBy;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("size", size);
            m.put("clients", clients);
            m.put("inUse", inUse);
            m.put("queued", queued);
            m.put("drainUs", drainUs);
            m.put("requestUs", requestUs);
            m.put("capacityPerSec", capacityPerSec);
            m.put("poolCeilingPerSec", poolCeilingPerSec);
            m.put("diskCeilingPerSec", diskCeilingPerSec);
            m.put("boundBy", boundBy);
            return m;
        }
    }
}
