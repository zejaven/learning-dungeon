package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSpringTransactionTest {

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
    void runtimeExceptionRollsBackByDefault() {
        String out = captureTrace(() -> {
            VisualSpringTransaction tx = new VisualSpringTransaction("orders");
            boolean committed = tx.transactional("createOrder")
                    .persist("order-1", "NEW")
                    .throwRuntime("IllegalStateException")
                    .complete();

            assertFalse(committed);
        });

        assertTrue(out.contains("SPRING_TX_DEFAULT_ROLLBACK"),
                "expected default unchecked rollback decision, got:\n" + out);
        assertTrue(out.contains("SPRING_TX_ROLLED_BACK"),
                "expected rollback event, got:\n" + out);
    }

    @Test
    void checkedExceptionCommitsByDefault() {
        String out = captureTrace(() -> {
            VisualSpringTransaction tx = new VisualSpringTransaction("imports");
            boolean committed = tx.transactional("importReport")
                    .persist("report-1", "PARSED")
                    .throwChecked("java.io.IOException")
                    .complete();

            assertTrue(committed);
        });

        assertTrue(out.contains("SPRING_TX_CHECKED_COMMIT"),
                "expected checked-exception commit decision, got:\n" + out);
        assertTrue(out.contains("SPRING_TX_COMMITTED"),
                "expected commit event, got:\n" + out);
    }

    @Test
    void rollbackForMakesCheckedExceptionRollBack() {
        String out = captureTrace(() -> {
            VisualSpringTransaction tx = new VisualSpringTransaction("imports");
            boolean committed = tx.transactional("importReport")
                    .rollbackFor("java.io.IOException")
                    .persist("report-2", "PARSED")
                    .throwChecked("java.io.IOException")
                    .complete();

            assertFalse(committed);
        });

        assertTrue(out.contains("SPRING_TX_ROLLBACK_FOR_MATCH"),
                "expected rollbackFor decision, got:\n" + out);
        assertTrue(out.contains("SPRING_TX_ROLLED_BACK"),
                "expected rollback event, got:\n" + out);
    }

    @Test
    void externalFailureAfterCommitDoesNotUndoCommittedRow() {
        String out = captureTrace(() -> {
            VisualSpringTransaction tx = new VisualSpringTransaction("orders");
            tx.transactional("createOrder")
                    .persist("order-9", "PAID")
                    .complete();

            tx.externalCallAfterCommitThrowsRuntime("emailClient", "EmailServiceDownException");
        });

        assertTrue(out.contains("SPRING_TX_COMMITTED"),
                "expected commit before external failure, got:\n" + out);
        assertTrue(out.contains("SPRING_EXTERNAL_EXCEPTION_AFTER_COMMIT"),
                "expected post-commit external failure event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSpringTransaction tx = new VisualSpringTransaction("orders");
            tx.transactional("createOrder")
                    .persist("order-1", "NEW")
                    .complete();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
