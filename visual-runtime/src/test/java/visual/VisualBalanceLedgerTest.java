package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualBalanceLedgerTest {

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
    void openingAnAccountEmitsReady() {
        String out = captureTrace(VisualBalanceLedger::appendOnly);
        assertTrue(out.contains("ACCOUNT_OPENED"), "expected a creation event, got:\n" + out);
    }

    @Test
    void inPlaceStorageOverwritesTheBalanceAndKeepsNoHistory() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.inPlace();
            book.credit(1000, "salary");
            book.debit(250, "groceries");
            book.explain();
            book.audit();
            book.report();
        });
        assertTrue(out.contains("OPERATION_APPLIED"), "expected an in-place update, got:\n" + out);
        assertTrue(out.contains("HISTORY_UNAVAILABLE"),
                "the balance must not be able to explain itself, got:\n" + out);
        assertTrue(out.contains("REBUILD_IMPOSSIBLE"),
                "there is nothing to recompute from, got:\n" + out);
        assertFalse(out.contains("ENTRY_APPENDED"), "in-place storage appends nothing, got:\n" + out);
        assertTrue(out.contains("Audit: balance 750, entries kept 0"),
                "the balance must be right and the history empty, got:\n" + out);
    }

    @Test
    void appendOnlyStorageKeepsEveryOperationAndDerivesTheBalance() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnly();
            book.credit(1000, "salary");
            book.debit(250, "groceries");
            book.explain();
            book.audit();
            book.report();
        });
        assertTrue(out.contains("ENTRY_APPENDED"), "expected an append, got:\n" + out);
        assertFalse(out.contains("OPERATION_APPLIED"), "a ledger never overwrites, got:\n" + out);
        assertTrue(out.contains("HISTORY_EXPLAINED"), "expected an itemized answer, got:\n" + out);
        assertTrue(out.contains("+1000 salary, -250 groceries = 750"),
                "the itemized answer must list every operation, got:\n" + out);
        assertTrue(out.contains("BALANCE_REBUILT"), "the balance must be derivable, got:\n" + out);
        assertTrue(out.contains("Audit: balance 750, entries kept 2"),
                "the same balance, with its history, got:\n" + out);
    }

    @Test
    void aReadIsOneRowInPlaceAndAFoldOverTheLedger() {
        String inPlace = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.inPlace();
            book.credit(1000, "salary");
            book.debit(250, "groceries");
            assertEquals(750, book.readBalance(), "in-place reads return the stored number");
        });
        assertTrue(inPlace.contains("the balance column"), "expected a single-row read, got:\n" + inPlace);
        assertTrue(inPlace.contains("1 row(s) touched"), "expected O(1), got:\n" + inPlace);

        String ledger = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnly();
            book.credit(1000, "salary");
            book.debit(250, "groceries");
            assertEquals(750, book.readBalance(), "a ledger read folds to the same number");
        });
        assertTrue(ledger.contains("a fold over every entry"), "expected a fold, got:\n" + ledger);
        assertTrue(ledger.contains("2 row(s) touched"),
                "the read cost must be the history length, got:\n" + ledger);
    }

    @Test
    void concurrentInPlaceUpdatesLoseOneOperation() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.inPlace();
            book.credit(1000, "opening");
            book.creditConcurrently(100, "refund", 200, "cashback");
            book.report();
        });
        assertTrue(out.contains("CONCURRENT_OPERATIONS"), "expected the overlap event, got:\n" + out);
        assertTrue(out.contains("LOST_UPDATE"), "a read-modify-write must lose one, got:\n" + out);
        assertTrue(out.contains("Audit: balance 1200"),
                "the balance should be 1300 but one write was overwritten, got:\n" + out);
    }

    @Test
    void concurrentAppendsCannotLoseAnOperation() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnly();
            book.credit(1000, "opening");
            book.creditConcurrently(100, "refund", 200, "cashback");
            book.report();
        });
        assertTrue(out.contains("CONCURRENT_OPERATIONS"), "expected the overlap event, got:\n" + out);
        assertFalse(out.contains("LOST_UPDATE"), "appends do not overwrite each other, got:\n" + out);
        assertTrue(out.contains("Audit: balance 1300, entries kept 3"),
                "both operations must survive, got:\n" + out);
    }

    @Test
    void inPlaceCorrectionLeavesNoEvidenceThatAnythingWasWrong() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.inPlace();
            book.credit(1000, "salary");
            book.debit(2500, "invoice #77");
            book.correctLast(250, "invoice #77 was 250, not 2500");
            book.explain();
            book.report();
        });
        assertTrue(out.contains("HISTORY_OVERWRITTEN"), "expected a silent rewrite, got:\n" + out);
        assertTrue(out.contains("HISTORY_UNAVAILABLE"),
                "the corrected balance still cannot justify itself, got:\n" + out);
        assertTrue(out.contains("Audit: balance 750"), "the number must end up right, got:\n" + out);
        assertTrue(out.contains("silent rewrites 1"), "the rewrite must be counted, got:\n" + out);
    }

    @Test
    void ledgerCorrectionAppendsAReversalAndKeepsTheMistake() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnly();
            book.credit(1000, "salary");
            book.debit(2500, "invoice #77");
            book.correctLast(250, "invoice #77 was 250, not 2500");
            book.report();
        });
        assertTrue(out.contains("CORRECTION_APPENDED"), "expected a correcting entry, got:\n" + out);
        assertTrue(out.contains("Audit: balance 750, entries kept 4"),
                "the wrong posting, its reversal and the fix must all remain, got:\n" + out);
    }

    @Test
    void deletingAnEntryQuietlyChangesTheBalanceTheLedgerRebuildsTo() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnly();
            book.credit(1000, "salary");
            book.debit(250, "groceries");
            book.deleteEntry(2);
            book.audit();
            book.report();
        });
        assertTrue(out.contains("LEDGER_MUTATED"), "expected the DELETE, got:\n" + out);
        assertTrue(out.contains("Audit: balance 1000, entries kept 1"),
                "the removed operation must be gone without a trace, got:\n" + out);
        assertTrue(out.contains("deleted entries 1"), "the deletion must be counted, got:\n" + out);
    }

    @Test
    void aSnapshotBoundsTheCostOfReadingTheBalance() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnly();
            for (int day = 1; day <= 8; day++) {
                book.credit(100, "day " + day);
            }
            assertEquals(800, book.readBalance(), "the fold covers every entry");
            book.takeSnapshot();
            book.credit(100, "day 9");
            assertEquals(900, book.readBalance(), "the snapshot plus what came after it");
            book.report();
        });
        assertTrue(out.contains("SNAPSHOT_TAKEN"), "expected a snapshot, got:\n" + out);
        assertTrue(out.contains("8 row(s) touched"), "the fold started at 8 rows, got:\n" + out);
        assertTrue(out.contains("1 row(s) touched"), "the snapshot must bound the fold, got:\n" + out);
        assertTrue(out.contains("Audit: balance 900, entries kept 9"),
                "a snapshot must not delete history, got:\n" + out);
    }

    @Test
    void aCachedBalanceIsCorrectOnlyWhileItIsWrittenInTheSameTransaction() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnlyWithCachedBalance();
            book.credit(1000, "salary");
            book.debit(250, "groceries");
            book.audit();
            book.debitInSeparateTransactions(90, "card fee");
            assertEquals(750, book.readBalance(), "reads still answer from the stale column");
            book.audit();
            book.report();
        });
        assertTrue(out.contains("BALANCE_REBUILT"),
                "one transaction keeps the two copies equal, got:\n" + out);
        assertTrue(out.contains("CACHE_DRIFT"), "expected the split write, got:\n" + out);
        assertTrue(out.contains("DRIFT_DETECTED"), "the audit must catch the drift, got:\n" + out);
        assertTrue(out.contains("the cached balance column"),
                "reads must come from the cached column, got:\n" + out);
    }

    @Test
    void appendOnlyStorageDoesNotEnforceABalanceInvariant() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnly();
            book.credit(500, "opening");
            assertTrue(book.debitWithFloor(300, 0, "rent"), "300 of 500 is allowed");
            assertFalse(book.debitWithFloor(300, 0, "rent again"), "200 cannot cover 300");
            book.debitConcurrentlyWithFloor(200, 0, "withdrawal");
            book.report();
        });
        assertTrue(out.contains("WITHDRAWAL_REJECTED"), "the serialized check works, got:\n" + out);
        assertTrue(out.contains("INVARIANT_VIOLATED"),
                "two overlapping withdrawals must both land, got:\n" + out);
        assertTrue(out.contains("Audit: balance -200"),
                "the account must end up below the floor, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualBalanceLedger book = VisualBalanceLedger.appendOnlyWithCachedBalance();
            book.credit(1000, "salary");
            book.debit(250, "groceries");
            book.creditConcurrently(50, "refund", 70, "cashback");
            book.correctLast(60, "cashback was 60");
            book.takeSnapshot();
            book.debitInSeparateTransactions(90, "card fee");
            book.debitWithFloor(10, 0, "coffee");
            book.debitConcurrentlyWithFloor(5, 0, "tip");
            book.deleteEntry(1);
            book.readBalance();
            book.explain();
            book.audit();
            book.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
