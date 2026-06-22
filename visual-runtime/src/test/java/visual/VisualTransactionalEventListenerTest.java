package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static visual.VisualTransactionalEventListener.Phase.AFTER_COMMIT;
import static visual.VisualTransactionalEventListener.Phase.AFTER_ROLLBACK;
import static visual.VisualTransactionalEventListener.Phase.BEFORE_COMMIT;

class VisualTransactionalEventListenerTest {

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
    void afterCommitListenerRunsOnlyAfterCommit() {
        String out = captureTrace(() -> {
            VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");
            app.listener("EmailReceipt", AFTER_COMMIT);

            boolean committed = app.transactional("placeOrder")
                    .persist("order-1", "PAID")
                    .publish("OrderPlaced")
                    .commit();

            assertTrue(committed);
        });

        assertTrue(out.contains("TX_EVENT_COMMITTED"),
                "expected commit event, got:\n" + out);
        assertTrue(out.contains("TX_EVENT_AFTER_COMMIT"),
                "expected AFTER_COMMIT listener event, got:\n" + out);
    }

    @Test
    void rollbackRunsAfterRollbackListener() {
        String out = captureTrace(() -> {
            VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");
            app.listener("EmailReceipt", AFTER_COMMIT);
            app.listener("RollbackAudit", AFTER_ROLLBACK);

            boolean committed = app.transactional("placeOrder")
                    .persist("order-2", "NEW")
                    .publish("OrderPlaced")
                    .rollback("payment declined");

            assertFalse(committed);
        });

        assertTrue(out.contains("TX_EVENT_ROLLED_BACK"),
                "expected rollback event, got:\n" + out);
        assertTrue(out.contains("TX_EVENT_AFTER_ROLLBACK"),
                "expected AFTER_ROLLBACK listener event, got:\n" + out);
    }

    @Test
    void beforeCommitFailureRollsBack() {
        String out = captureTrace(() -> {
            VisualTransactionalEventListener app = new VisualTransactionalEventListener("inventory");
            app.listener("StockGuard", BEFORE_COMMIT);

            boolean committed = app.transactional("reserveStock")
                    .persist("reservation-1", "PENDING")
                    .publish("StockReserved")
                    .listenerFails("StockGuard", "OutOfStockException")
                    .commit();

            assertFalse(committed);
        });

        assertTrue(out.contains("TX_EVENT_BEFORE_COMMIT_FAILED"),
                "expected BEFORE_COMMIT failure event, got:\n" + out);
        assertTrue(out.contains("TX_EVENT_ROLLED_BACK"),
                "expected rollback after BEFORE_COMMIT failure, got:\n" + out);
    }

    @Test
    void noTransactionSkipsUnlessFallbackExecutionIsEnabled() {
        String out = captureTrace(() -> {
            VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");
            app.listener("EmailReceipt", AFTER_COMMIT);
            app.listener("ImmediateAudit", AFTER_COMMIT, true);

            app.publishOutsideTransaction("OrderPlaced");
        });

        assertTrue(out.contains("TX_EVENT_NO_TRANSACTION_SKIPPED"),
                "expected skipped listener without fallback, got:\n" + out);
        assertTrue(out.contains("TX_EVENT_FALLBACK_EXECUTED"),
                "expected fallback listener to execute, got:\n" + out);
    }

    @Test
    void afterCommitFailureCannotUndoCommit() {
        String out = captureTrace(() -> {
            VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");
            app.listener("EmailReceipt", AFTER_COMMIT);

            boolean committed = app.transactional("placeOrder")
                    .persist("order-3", "PAID")
                    .publish("OrderPlaced")
                    .listenerFails("EmailReceipt", "MailServerDownException")
                    .commit();

            assertTrue(committed);
        });

        assertTrue(out.contains("TX_EVENT_COMMITTED"),
                "expected commit before listener failure, got:\n" + out);
        assertTrue(out.contains("TX_EVENT_AFTER_COMMIT_FAILED"),
                "expected after-commit failure event, got:\n" + out);
        assertTrue(out.contains("\"database\":[{\"id\":\"order-3\""),
                "expected committed row to remain visible, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualTransactionalEventListener app = new VisualTransactionalEventListener("orders");
            app.listener("EmailReceipt", AFTER_COMMIT);
            app.transactional("placeOrder")
                    .persist("order-4", "PAID")
                    .publish("OrderPlaced")
                    .commit();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
