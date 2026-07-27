package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSaleDedupTest {

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
    void creatingTheServiceEmitsReady() {
        String out = captureTrace(VisualSaleDedup::withUpsert);
        assertTrue(out.contains("LEDGER_READY"), "expected a creation event, got:\n" + out);
    }

    @Test
    void withoutAGuardTheRetryBecomesASecondSale() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withoutGuard();
            db.receive("sale-1", "coffee", 250);
            db.receive("sale-1", "coffee", 250);
            db.report();
        });
        assertTrue(out.contains("DUPLICATE_REGISTERED"), "expected a duplicate row, got:\n" + out);
        assertTrue(out.contains("Audit: 2 sale row(s) totalling 500"),
                "the same sale must be counted twice, got:\n" + out);
    }

    @Test
    void upsertBlocksTheRetryAndReplaysTheStoredResponse() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withUpsert();
            db.receive("sale-1", "coffee", 250);
            boolean applied = db.receive("sale-1", "coffee", 250);
            assertFalse(applied, "a retry of a known key must not insert a second row");
            db.report();
        });
        assertTrue(out.contains("DUPLICATE_BLOCKED"), "expected the retry to be blocked, got:\n" + out);
        assertTrue(out.contains("201 created"), "the stored response must be replayed, got:\n" + out);
        assertTrue(out.contains("Audit: 1 sale row(s) totalling 250"),
                "the sale must be registered exactly once, got:\n" + out);
    }

    @Test
    void uniqueIndexRejectsTheSecondInsertItself() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();
            db.receive("sale-1", "coffee", 250);
            db.receive("sale-1", "coffee", 250);
            db.report();
        });
        assertTrue(out.contains("CONSTRAINT_VIOLATION"),
                "the write itself must be the check, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_BLOCKED"), "expected the retry to be blocked, got:\n" + out);
        assertTrue(out.contains("Audit: 1 sale row(s) totalling 250"),
                "the sale must be registered exactly once, got:\n" + out);
    }

    @Test
    void checkThenInsertIsCorrectOnlyWhileRequestsAreSerialized() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withCheckThenInsert();
            db.receive("sale-1", "coffee", 250);
            db.receive("sale-1", "coffee", 250);
            db.report();
        });
        assertTrue(out.contains("DEDUP_CHECK"), "expected the SELECT step, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_BLOCKED"), "a serialized retry is caught, got:\n" + out);
        assertTrue(out.contains("Audit: 1 sale row(s) totalling 250"),
                "the sale must be registered exactly once, got:\n" + out);
    }

    @Test
    void checkThenInsertLosesTheRaceWhenTwoRequestsOverlap() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withCheckThenInsert();
            db.receiveConcurrently("sale-1", "coffee", 250);
            db.report();
        });
        assertTrue(out.contains("CONCURRENT_ARRIVAL"), "expected the overlap event, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_REGISTERED"),
                "both requests must pass the check and insert, got:\n" + out);
        assertTrue(out.contains("Audit: 2 sale row(s) totalling 500"),
                "the race must produce a second row, got:\n" + out);
    }

    @Test
    void uniqueIndexArbitratesTheSameRace() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();
            db.receiveConcurrently("sale-1", "coffee", 250);
            db.report();
        });
        assertTrue(out.contains("CONSTRAINT_VIOLATION"),
                "the database must reject the loser, got:\n" + out);
        assertFalse(out.contains("DUPLICATE_REGISTERED"),
                "no duplicate row may survive the race, got:\n" + out);
        assertTrue(out.contains("Audit: 1 sale row(s) totalling 250"),
                "the sale must be registered exactly once, got:\n" + out);
    }

    @Test
    void contentHashDedupDropsAGenuinelyDifferentSale() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.dedupingByContentHash();
            db.receive("sale-1", "coffee", 250);
            // A second customer, a second real sale, the same coffee for the same price.
            boolean applied = db.receive("sale-2", "coffee", 250);
            assertFalse(applied, "content hashing cannot tell two identical sales apart");
            db.report();
        });
        assertTrue(out.contains("GENUINE_SALE_LOST"), "expected a lost sale, got:\n" + out);
        assertTrue(out.contains("Audit: 1 sale row(s) totalling 250"),
                "the second sale must be missing, got:\n" + out);
    }

    @Test
    void contentHashStillBlocksAnHonestRetryOfTheSameKey() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.dedupingByContentHash();
            db.receive("sale-1", "coffee", 250);
            db.receive("sale-1", "coffee", 250);
        });
        assertTrue(out.contains("DUPLICATE_BLOCKED"), "a real retry is still a duplicate, got:\n" + out);
        assertFalse(out.contains("GENUINE_SALE_LOST"),
                "the same key is not a different sale, got:\n" + out);
    }

    @Test
    void aDedupRowCommittedWithoutItsSaleSwallowsTheSaleForever() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();
            db.receiveSplitCommit("sale-1", "coffee", 250);
            db.receive("sale-1", "coffee", 250);
            db.report();
        });
        assertTrue(out.contains("CRASH_BETWEEN_WRITES"), "expected the crash event, got:\n" + out);
        assertTrue(out.contains("SALE_SILENTLY_LOST"),
                "the retry must be deduplicated against a sale that does not exist, got:\n" + out);
        assertTrue(out.contains("Audit: 0 sale row(s) totalling 0"),
                "no sale row may exist, got:\n" + out);
    }

    @Test
    void purgingKeysTooEarlyReopensTheDuplicateHole() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();
            db.receive("sale-1", "coffee", 250);
            db.advanceDays(3);
            db.purgeDedupKeys(1);
            db.receive("sale-1", "coffee", 250);
            db.report();
        });
        assertTrue(out.contains("TIME_PASSED"), "expected the clock to move, got:\n" + out);
        assertTrue(out.contains("KEYS_PURGED"), "expected the retention job, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_REGISTERED"),
                "a retry after the window is invisible to the guard, got:\n" + out);
        assertTrue(out.contains("Audit: 2 sale row(s) totalling 500"),
                "the late retry must have duplicated the sale, got:\n" + out);
    }

    @Test
    void aKnownKeyWithADifferentBodyIsAConflict() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withUpsert();
            db.receive("sale-1", "coffee", 250);
            boolean applied = db.receive("sale-1", "cake", 500);
            assertFalse(applied, "a conflicting body must not be registered");
            db.report();
        });
        assertTrue(out.contains("KEY_CONFLICT"), "expected a 409, got:\n" + out);
        assertFalse(out.contains("DUPLICATE_BLOCKED"),
                "a different body must not be answered with the stored response, got:\n" + out);
        assertTrue(out.contains("Audit: 1 sale row(s) totalling 250"),
                "only the first sale may exist, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSaleDedup db = VisualSaleDedup.withUniqueIndex();
            db.receive("sale-1", "coffee", 250);
            db.receiveConcurrently("sale-2", "bagel", 180);
            db.receiveSplitCommit("sale-3", "tea", 120);
            db.receive("sale-3", "tea", 120);
            db.advanceDays(2);
            db.purgeDedupKeys(30);
            db.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
