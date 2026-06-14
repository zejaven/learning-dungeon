package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualInboxTest {

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
    void creatingAnInboxEmitsCreated() {
        String out = captureTrace(() -> new VisualInbox("payments"));
        assertTrue(out.contains("INBOX_CREATED"), "expected a creation event, got:\n" + out);
    }

    @Test
    void newMessageIsProcessedOnce() {
        String out = captureTrace(() -> {
            VisualInbox inbox = new VisualInbox("payments");
            boolean processed = inbox.receive("m1", "charge card", 100);
            assertTrue(processed, "a brand-new id must be processed");
        });
        assertTrue(out.contains("MESSAGE_RECEIVED"), "expected a received event, got:\n" + out);
        assertTrue(out.contains("MESSAGE_PROCESSED"), "expected a processed event, got:\n" + out);
    }

    @Test
    void redeliveredMessageIsSkippedAndEffectIsNotDoubled() {
        String out = captureTrace(() -> {
            VisualInbox inbox = new VisualInbox("payments");
            boolean first = inbox.receive("m1", "charge card", 100);
            boolean second = inbox.receive("m1", "charge card", 100);
            assertTrue(first, "first delivery is processed");
            assertFalse(second, "redelivery of the same id must be skipped");
        });
        assertTrue(out.contains("DUPLICATE_SKIPPED"), "expected a duplicate-skipped event, got:\n" + out);
        // The total effect is reported in the processed/duplicate descriptions; the
        // single processing must leave the total at 100, never 200.
        assertTrue(out.contains("total is now 100"), "effect must apply exactly once, got:\n" + out);
        assertFalse(out.contains("total is now 200"), "effect must not double, got:\n" + out);
    }

    @Test
    void dedupIsByIdNotByPayload() {
        String out = captureTrace(() -> {
            VisualInbox inbox = new VisualInbox("payments");
            // Same payload, different ids -> both are distinct messages.
            boolean a = inbox.receive("m1", "charge card", 100);
            boolean b = inbox.receive("m2", "charge card", 100);
            assertTrue(a && b, "different ids are different messages even with identical payload");
        });
        assertTrue(out.contains("total is now 200"), "both distinct ids must apply, got:\n" + out);
        assertFalse(out.contains("DUPLICATE_SKIPPED"), "identical payload is not a duplicate, got:\n" + out);
    }

    @Test
    void cleanupCanLetALateDuplicateReprocess() {
        String out = captureTrace(() -> {
            VisualInbox inbox = new VisualInbox("payments");
            inbox.receive("m1", "charge card", 100);
            inbox.cleanup(0);                       // purge everything (window too short)
            boolean reprocessed = inbox.receive("m1", "charge card", 100);
            assertTrue(reprocessed, "a purged id is treated as new and reprocessed");
        });
        assertTrue(out.contains("INBOX_CLEANUP"), "expected a cleanup event, got:\n" + out);
        assertTrue(out.contains("total is now 200"), "purged id double-applies, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualInbox inbox = new VisualInbox("payments");
            inbox.receive("m1", "charge card", 100);
            inbox.receive("m1", "charge card", 100);
            inbox.cleanup(1);
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
