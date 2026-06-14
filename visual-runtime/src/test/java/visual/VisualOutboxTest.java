package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualOutboxTest {

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
    void creatingAnOutboxEmitsCreated() {
        String out = captureTrace(() -> new VisualOutbox("orders"));
        assertTrue(out.contains("OUTBOX_CREATED"), "expected a creation event, got:\n" + out);
    }

    @Test
    void placingAnOrderStagesAnEventInOneTransaction() {
        String out = captureTrace(() -> {
            VisualOutbox outbox = new VisualOutbox("orders");
            outbox.placeOrder("o1", 100);
        });
        assertTrue(out.contains("TRANSACTION_COMMITTED"), "expected a commit event, got:\n" + out);
        assertTrue(out.contains("PENDING"), "the staged outbox row must be PENDING, got:\n" + out);
    }

    @Test
    void relayPublishesPendingEvents() {
        String out = captureTrace(() -> {
            VisualOutbox outbox = new VisualOutbox("orders");
            outbox.placeOrder("o1", 100);
            outbox.relayPublish();
        });
        assertTrue(out.contains("EVENT_PUBLISHED"), "expected a publish event, got:\n" + out);
    }

    @Test
    void failingTransactionStagesNothing() {
        String out = captureTrace(() -> {
            VisualOutbox outbox = new VisualOutbox("orders");
            outbox.placeOrderFailing("o1", 100);
            // A failed transaction leaves nothing for the relay to publish.
            outbox.relayPublish();
        });
        assertTrue(out.contains("TRANSACTION_ROLLED_BACK"), "expected a rollback event, got:\n" + out);
        assertFalse(out.contains("EVENT_PUBLISHED\",\"description") && out.contains("OrderPlaced"),
                "nothing should have been staged or published, got:\n" + out);
    }

    @Test
    void dualWriteLosesTheEvent() {
        String out = captureTrace(() -> {
            VisualOutbox outbox = new VisualOutbox("orders");
            outbox.placeOrderDualWrite("o1", 100);
        });
        assertTrue(out.contains("DUAL_WRITE_LOST"), "expected a dual-write-lost event, got:\n" + out);
    }

    @Test
    void relayCrashCausesRedelivery() {
        String out = captureTrace(() -> {
            VisualOutbox outbox = new VisualOutbox("orders");
            outbox.placeOrder("o1", 100);
            outbox.relayCrashAfterSend();   // broker gets e1, but row stays PENDING
            outbox.relayPublish();          // sends e1 again -> duplicate
        });
        assertTrue(out.contains("EVENT_REDELIVERED"), "expected a redelivery event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualOutbox outbox = new VisualOutbox("orders");
            outbox.placeOrder("o1", 100);
            outbox.placeOrderFailing("o2", 50);
            outbox.relayPublish();
            outbox.placeOrderDualWrite("o3", 75);
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
