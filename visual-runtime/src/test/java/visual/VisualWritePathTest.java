package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualWritePathTest {

    private String captureTrace(Runnable body) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void creatingASceneAnnouncesItIsACostModel() {
        String out = captureTrace(() -> VisualWritePath.table("documents"));
        assertTrue(out.contains("WRITER_CONFIGURED"), "expected a configuration event, got:\n" + out);
        assertTrue(out.contains("fixed cost model"),
                "the model must not be mistaken for a benchmark, got:\n" + out);
    }

    @Test
    void anAlreadyCompressedThirtyMegabyteValueBecomesThousandsOfToastChunks() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents").payload(30, false);
            writer.explainPayload();
        });
        assertTrue(out.contains("TOAST_SPILL"), "expected the payload to spill out of line, got:\n" + out);
        assertTrue(out.contains("gives nothing back"),
                "compressing compressed bytes must be shown as wasted, got:\n" + out);
        assertTrue(out.contains("15729 TOAST chunk rows"),
                "30 MB must slice into 15729 chunks of 2000 B, got:\n" + out);
        assertTrue(out.contains("One INSERT is really 15730 row writes"),
                "the chunk rows are the real write amplification, got:\n" + out);
    }

    @Test
    void compressiblePayloadShrinksBeforeItIsChunked() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents").payload(30, true);
            writer.explainPayload();
        });
        assertTrue(out.contains("compression shrinks it to 7.5 MB"),
                "compressible data must actually compress, got:\n" + out);
        assertTrue(out.contains("3933 TOAST chunk rows"),
                "fewer stored bytes means fewer chunks, got:\n" + out);
    }

    @Test
    void externalStorageSkipsThePointlessCompressionAttempt() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents")
                    .payload(30, false)
                    .externalStorage();
            writer.explainPayload();
            writer.insertRowByRow(1);
        });
        assertTrue(out.contains("the compression attempt is skipped"),
                "SET STORAGE EXTERNAL must stop the attempt, got:\n" + out);
        assertTrue(out.contains("one write now costs 122.359 ms"),
                "skipping the attempt must save the 27 ms it cost, got:\n" + out);
    }

    @Test
    void everyByteOfPayloadIsWrittenTwice() {
        String out = captureTrace(() -> VisualWritePath.table("documents")
                .payload(30, false)
                .explainWal());
        assertTrue(out.contains("WAL_AMPLIFIED"), "expected the WAL breakdown, got:\n" + out);
        assertTrue(out.contains("30.0 MB of payload costs 60.9 MB of disk"),
                "table plus WAL is roughly double, got:\n" + out);
    }

    @Test
    void indexesAreNoiseAtThirtyMegabytesAndRealOnceTheRowIsSmall() {
        String big = captureTrace(() -> VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .explainIndexes());
        assertTrue(big.contains("INDEX_MAINTENANCE"), "expected the index breakdown, got:\n" + big);
        assertTrue(big.contains("the indexes are noise"),
                "0.3 ms against a 149 ms write must not be called a problem, got:\n" + big);

        String small = captureTrace(() -> VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(8)
                .explainIndexes());
        assertTrue(small.contains("index maintenance is a real share of the write"),
                "once the bytes are gone the indexes are the write, got:\n" + small);
    }

    @Test
    void batchingBarelyMovesAWriteThatIsDominatedByBytes() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents")
                    .payload(30, false)
                    .secondaryIndexes(4);
            writer.insertBatch(4);
        });
        assertTrue(out.contains("BATCH_EXECUTED"), "expected the batch event, got:\n" + out);
        assertTrue(out.contains("598.396 ms -> 592.696 ms, saving 5.700 ms (0.9%)"),
                "the standard advice must be shown to buy almost nothing here, got:\n" + out);
    }

    @Test
    void movingThePayloadOutOfTheDatabaseIsTheOrderOfMagnitudeFix() {
        String out = captureTrace(() -> VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .offloadPayload(512));
        assertTrue(out.contains("PAYLOAD_OFFLOADED"), "expected the offload event, got:\n" + out);
        assertTrue(out.contains("149.599 ms to 2.261 ms"),
                "the write must collapse once the bytes leave, got:\n" + out);
        assertTrue(out.contains("66.1x faster"), "expected the speedup, got:\n" + out);
    }

    @Test
    void batchingPaysOnceTheRowsAreSmall() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents")
                    .offloadPayload(512)
                    .secondaryIndexes(2);
            writer.insertRowByRow(40);
            writer.insertBatch(40);
        });
        assertTrue(out.contains("ROWS_INSERTED"),
                "rows past the detail limit must be folded into one event, got:\n" + out);
        assertTrue(out.contains("85.640 ms -> 11.540 ms, saving 74.100 ms (86.5%)"),
                "with the bytes gone, batching is the whole win, got:\n" + out);
    }

    @Test
    void copyRemovesThePerRowParseThatBatchingKeeps() {
        String out = captureTrace(() -> VisualWritePath.table("documents")
                .offloadPayload(512)
                .secondaryIndexes(2)
                .copyIn(40));
        assertTrue(out.contains("COPY_STREAMED"), "expected the COPY event, got:\n" + out);
        assertTrue(out.contains("0.006 ms per row instead of 0.060 ms"),
                "COPY must skip the per-row parse, got:\n" + out);
        assertTrue(out.contains("11.540 ms batched -> 9.380 ms with COPY"),
                "COPY must beat a batch of the same rows, got:\n" + out);
    }

    @Test
    void asyncCommitTradesTheFsyncWaitForAWindowOfLoss() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents")
                    .offloadPayload(512)
                    .secondaryIndexes(2)
                    .asyncCommit();
            writer.insertRowByRow(40);
        });
        assertTrue(out.contains("COMMIT_UNFLUSHED"), "expected the unflushed commits, got:\n" + out);
        assertTrue(out.contains("0.800 ms instead of 60.000 ms"),
                "40 fsyncs must be what is being given up, got:\n" + out);
        assertFalse(out.contains("COMMIT_FLUSHED"),
                "synchronous_commit = off must not flush, got:\n" + out);
    }

    @Test
    void aThirtyMegabyteRowIsDiskBoundNoMatterHowManyConnectionsYouAdd() {
        String small = captureTrace(() -> VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .connectionPool(20)
                .serveApi(300));
        String large = captureTrace(() -> VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .connectionPool(200)
                .serveApi(300));
        assertTrue(small.contains("POOL_SATURATED"), "expected a saturated pool, got:\n" + small);
        assertTrue(small.contains("the limit is the disk, not the pool"),
                "60.9 MB per row must hit the bandwidth ceiling first, got:\n" + small);
        assertTrue(small.contains("The burst of 300 takes 37.5 s to clear"),
                "expected the drain time, got:\n" + small);
        assertTrue(large.contains("The burst of 300 takes 37.5 s to clear"),
                "ten times the connections must not move the ceiling, got:\n" + large);
    }

    @Test
    void aMetadataRowIsPoolBoundInsteadOfDiskBound() {
        String out = captureTrace(() -> VisualWritePath.table("documents")
                .payload(30, false)
                .secondaryIndexes(4)
                .offloadPayload(512)
                .connectionPool(20)
                .serveApi(300));
        assertTrue(out.contains("the limit is the connections"),
                "once the row is 512 B the disk is no longer the constraint, got:\n" + out);
        assertTrue(out.contains("The burst of 300 takes 33.917 ms to clear"),
                "the same burst must now clear in milliseconds, got:\n" + out);
    }

    @Test
    void aPoolWithHeadroomOnlyMakesCallersWaitForTheirOwnWrite() {
        String out = captureTrace(() -> VisualWritePath.table("documents")
                .offloadPayload(512)
                .connectionPool(20)
                .serveApi(15));
        assertTrue(out.contains("POOL_HEADROOM"), "expected headroom, got:\n" + out);
        assertFalse(out.contains("POOL_SATURATED"), "15 callers must fit in 20 connections, got:\n" + out);
    }

    @Test
    void theReportSumsEverythingTheSceneWrote() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents")
                    .offloadPayload(512)
                    .secondaryIndexes(2);
            writer.insertRowByRow(4);
            writer.insertBatch(40, 10);
            writer.report();
        });
        assertTrue(out.contains("WRITE_REPORT"), "expected the report, got:\n" + out);
        assertTrue(out.contains("Total: 44 row(s), 8 round trip(s), 8 commit(s)"),
                "4 single inserts plus 4 batches of 10, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualWritePath writer = VisualWritePath.table("documents")
                    .payload(30, true)
                    .secondaryIndexes(3)
                    .externalStorage()
                    .connectionPool(10);
            writer.explainPayload();
            writer.explainWal();
            writer.explainIndexes();
            writer.insertRowByRow(6);
            writer.insertBatch(6, 2);
            writer.copyIn(6);
            writer.serveApi(50);
            writer.offloadPayload(512).asyncCommit();
            writer.explainPayload();
            writer.insertRowByRow(6);
            writer.serveApi(5);
            writer.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
