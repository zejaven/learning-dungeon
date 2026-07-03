package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualServiceCallsTest {

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
    void emitsTimeoutAndFallbackForUnavailableService() {
        String out = captureTrace(() -> {
            VisualServiceCalls calls = new VisualServiceCalls("checkout");
            calls.deadline(300)
                    .service("inventory", 5_000, false)
                    .callWithFallback("inventory", 120, "cached stock");
        });

        assertTrue(out.contains("SERVICE_TIMEOUT"),
                "expected a timeout event, got:\n" + out);
        assertTrue(out.contains("SERVICE_FALLBACK_USED"),
                "expected a fallback event, got:\n" + out);
    }

    @Test
    void emitsParallelJoinForIndependentCalls() {
        String out = captureTrace(() -> {
            VisualServiceCalls calls = new VisualServiceCalls("product-page");
            calls.service("catalog", 70, true)
                    .service("price", 90, true)
                    .service("reviews", 2_000, false)
                    .callParallel(150, "catalog", "price", "reviews");
        });

        assertTrue(out.contains("PARALLEL_CALLS_JOINED"),
                "expected a parallel join event, got:\n" + out);
        assertTrue(out.contains("\"savedMs\""),
                "expected saved time in state, got:\n" + out);
    }

    @Test
    void opensCircuitAndThenShortCircuits() {
        String out = captureTrace(() -> {
            VisualServiceCalls calls = new VisualServiceCalls("gateway");
            calls.service("recommendations", 4_000, false);
            calls.callWithCircuitBreaker("recommendations", 100, 2, "popular products");
            calls.callWithCircuitBreaker("recommendations", 100, 2, "popular products");
            calls.callWithCircuitBreaker("recommendations", 100, 2, "popular products");
        });

        assertTrue(out.contains("CIRCUIT_OPENED"),
                "expected an open-circuit event, got:\n" + out);
        assertTrue(out.contains("CIRCUIT_SHORT_CIRCUITED"),
                "expected a short-circuit event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualServiceCalls calls = new VisualServiceCalls("gateway");
            calls.service("profile", 60, true);
            calls.call("profile", 100);
            calls.completeResponse("profile page");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
