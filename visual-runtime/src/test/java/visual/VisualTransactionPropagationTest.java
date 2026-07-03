package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import visual.VisualTransactionPropagation.Propagation;

class VisualTransactionPropagationTest {

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
    void requiredInnerJoinsOuterPhysicalTransaction() {
        String out = captureTrace(() -> {
            VisualTransactionPropagation tx = new VisualTransactionPropagation();
            tx.enter("placeOrder", Propagation.REQUIRED);
            tx.enter("writeAudit", Propagation.REQUIRED);
            tx.commit();
            tx.commit();
        });

        assertEvent(out, "TX_START_PHYSICAL");
        assertEvent(out, "TX_JOIN");
        assertEvent(out, "TX_COMMIT");
        // Only one physical transaction should ever be created.
        assertTrue(!out.contains("\"id\":\"T2\""),
                "REQUIRED must reuse one physical transaction, got:\n" + out);
    }

    @Test
    void requiresNewSuspendsOuterAndStartsSecondTransaction() {
        String out = captureTrace(() -> {
            VisualTransactionPropagation tx = new VisualTransactionPropagation();
            tx.enter("placeOrder", Propagation.REQUIRED);
            tx.enter("writeAudit", Propagation.REQUIRES_NEW);
            tx.commit();
            tx.commit();
        });

        assertEvent(out, "TX_SUSPEND");
        assertEvent(out, "TX_RESUME");
        assertTrue(out.contains("\"id\":\"T2\""),
                "REQUIRES_NEW must open a second physical transaction, got:\n" + out);
    }

    @Test
    void nestedRollsBackToSavepointWithoutKillingOuter() {
        String out = captureTrace(() -> {
            VisualTransactionPropagation tx = new VisualTransactionPropagation();
            tx.enter("importBatch", Propagation.REQUIRED);
            tx.enter("importRow", Propagation.NESTED);
            tx.rollback();
            tx.commit();
        });

        assertEvent(out, "TX_SAVEPOINT");
        assertEvent(out, "TX_ROLLBACK_SAVEPOINT");
        assertEvent(out, "TX_COMMIT");
    }

    @Test
    void innerRequiredRollbackPoisonsSharedTransaction() {
        String out = captureTrace(() -> {
            VisualTransactionPropagation tx = new VisualTransactionPropagation();
            tx.enter("placeOrder", Propagation.REQUIRED);
            tx.enter("writeAudit", Propagation.REQUIRED);
            tx.rollback();
            tx.commit();
        });

        assertEvent(out, "TX_MARK_ROLLBACK");
        assertEvent(out, "TX_UNEXPECTED_ROLLBACK");
        assertTrue(out.contains("\"rollbackOnly\":true"),
                "shared transaction must become rollback-only, got:\n" + out);
    }

    @Test
    void mandatoryWithoutTransactionRaisesPropagationError() {
        String out = captureTrace(() -> {
            VisualTransactionPropagation tx = new VisualTransactionPropagation();
            tx.enter("sendInvoice", Propagation.MANDATORY);
        });

        assertEvent(out, "TX_ERROR");
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualTransactionPropagation tx = new VisualTransactionPropagation();
            tx.enter("placeOrder", Propagation.REQUIRED);
            tx.enter("readReport", Propagation.NOT_SUPPORTED);
            tx.commit();
            tx.commit();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }

    private static void assertEvent(String out, String event) {
        assertTrue(out.contains("\"event\":\"" + event + "\""),
                "expected " + event + ", got:\n" + out);
    }
}
