package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualRetryTest {

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
    void backsOffExponentiallyAndSucceeds() {
        String out = captureTrace(() -> {
            VisualRetry retry = new VisualRetry("orders");
            retry.maxAttempts(4).backoff(100, 2.0, 1_000);
            retry.script("503", "503", "200");
            assertTrue(retry.call("GET /orders/7"));
        });
        assertTrue(out.contains("BACKOFF_SCHEDULED"), out);
        assertTrue(out.contains("Backoff before attempt 3: 200 ms"), out);
        assertTrue(out.contains("CALL_SUCCEEDED"), out);
    }

    @Test
    void doesNotRetryPermanentErrors() {
        String out = captureTrace(() -> {
            VisualRetry retry = new VisualRetry();
            retry.script("400");
            assertFalse(retry.call("GET /orders/x"));
        });
        assertTrue(out.contains("RETRY_SKIPPED_NON_RETRYABLE"), out);
        assertFalse(out.contains("BACKOFF_SCHEDULED"), out);
    }

    @Test
    void ambiguousFailureNeedsIdempotency() {
        String out = captureTrace(() -> {
            VisualRetry retry = new VisualRetry();
            retry.script("TIMEOUT", "201");
            assertFalse(retry.call("POST /payments"));
            assertTrue(retry.callWithKey("POST /payments", "pay-42"));
        });
        assertTrue(out.contains("RETRY_SKIPPED_NOT_IDEMPOTENT"), out);
        assertTrue(out.contains("CALL_SUCCEEDED"), out);
    }

    @Test
    void honorsRetryAfter() {
        String out = captureTrace(() -> {
            VisualRetry retry = new VisualRetry();
            retry.script("429:800", "200");
            assertTrue(retry.call("GET /quotes"));
        });
        assertTrue(out.contains("RETRY_AFTER_HONORED"), out);
    }

    @Test
    void stopsAtDeadlineAndBudget() {
        String out = captureTrace(() -> {
            VisualRetry retry = new VisualRetry();
            retry.maxAttempts(5).backoff(100, 2.0, 1_000).deadline(500).retryBudget(10);
            retry.script("503");
            assertFalse(retry.call("GET /a"));
            VisualRetry budget = new VisualRetry();
            budget.maxAttempts(3).retryBudget(1);
            budget.script("503");
            assertFalse(budget.call("GET /b"));
        });
        assertTrue(out.contains("DEADLINE_EXCEEDED"), out);
        assertTrue(out.contains("RETRY_BUDGET_EXHAUSTED"), out);
    }

    @Test
    void jitterSpreadsTheHerd() {
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger spread = new AtomicInteger();
        String out = captureTrace(() -> {
            VisualRetry retry = new VisualRetry();
            retry.maxAttempts(2).backoff(400, 2.0, 2_000);
            synced.set(retry.herd(20, 50));
            retry.jitter("FULL");
            spread.set(retry.herd(20, 50));
        });
        assertEquals(20, synced.get());
        assertTrue(spread.get() < 20, "peak with jitter: " + spread.get());
        assertTrue(out.contains("HERD_RETRIES_SYNCHRONIZED"), out);
        assertTrue(out.contains("HERD_RETRIES_SPREAD"), out);
    }

    @Test
    void retriesAtEveryLayerMultiply() {
        AtomicInteger all = new AtomicInteger();
        AtomicInteger one = new AtomicInteger();
        String out = captureTrace(() -> {
            VisualRetry retry = new VisualRetry();
            retry.maxAttempts(3);
            List<String> stack = List.of("gateway", "orders", "inventory", "db");
            all.set(retry.amplify(stack, List.of("gateway", "orders", "inventory")));
            one.set(retry.amplify(stack, List.of("inventory")));
        });
        assertEquals(27, all.get());
        assertEquals(3, one.get());
        assertTrue(out.contains("RETRY_AMPLIFIED"), out);
        assertTrue(out.contains("RETRY_CONTAINED"), out);
    }
}
