package visual;

import org.junit.jupiter.api.Test;
import visual.VisualIdempotency.Body;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualIdempotencyTest {

    private static VisualIdempotency shop() {
        return VisualIdempotency.serving("/orders",
                Body.of("item", "tea").and("qty", "2").and("status", "new"),
                Body.of("item", "cups").and("qty", "6").and("status", "paid"));
    }

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
    void servingASeededCollectionEmitsReady() {
        String out = captureTrace(VisualIdempotencyTest::shop);
        assertTrue(out.contains("SERVER_READY"), "expected a startup event, got:\n" + out);
        assertTrue(out.contains("/orders/1, /orders/2"), "the seeded paths must be visible, got:\n" + out);
    }

    @Test
    void aReadIsReportedAsSafeAndThereforeIdempotent() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.get("/orders/1");
            api.head("/orders/1");
            api.report();
        });
        assertTrue(out.contains("SAFE_READ"), "a GET must be reported as safe, got:\n" + out);
        assertFalse(out.contains("EFFECT_APPLIED"), "a read may not change state, got:\n" + out);
        assertTrue(out.contains("0 actually changed state"), "no write may be counted, got:\n" + out);
    }

    @Test
    void repeatingTheSamePutLeavesTheSameState() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            Body target = Body.of("item", "tea").and("qty", "5").and("status", "paid");
            api.put("/orders/1", target);
            api.put("/orders/1", Body.of("item", "tea").and("qty", "5").and("status", "paid"));
            api.report();
        });
        assertTrue(out.contains("EFFECT_APPLIED"), "the first PUT must apply, got:\n" + out);
        assertTrue(out.contains("IDEMPOTENT_REPEAT"), "the second PUT must change nothing, got:\n" + out);
        assertFalse(out.contains("DUPLICATE_EFFECT"), "a repeated PUT is not a duplicate, got:\n" + out);
        assertTrue(out.contains("1 actually changed state"), "only one write happened, got:\n" + out);
    }

    @Test
    void aLostResponseIsReportedAsAmbiguousAndTheRetryIsIdentical() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.dropNextResponse();
            api.put("/orders/1", Body.of("item", "tea").and("qty", "5").and("status", "paid"));
            api.retry();
            api.report();
        });
        assertTrue(out.contains("NETWORK_UNRELIABLE"), "expected the network warning, got:\n" + out);
        assertTrue(out.contains("RESPONSE_LOST"), "the answer must be reported as lost, got:\n" + out);
        assertTrue(out.contains("RETRY_SENT"), "the client must resend the request, got:\n" + out);
        assertTrue(out.contains("IDEMPOTENT_REPEAT"), "the retry must be harmless, got:\n" + out);
        assertTrue(out.contains("answer(s) never reached the client"), "expected the audit, got:\n" + out);
    }

    @Test
    void retryingAPostCreatesASecondResource() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.dropNextResponse();
            api.post("/orders", Body.of("item", "mugs").and("qty", "1").and("status", "new"));
            api.retry();
            api.report();
        });
        assertTrue(out.contains("DUPLICATE_EFFECT"), "POST is not idempotent, got:\n" + out);
        assertFalse(out.contains("IDEMPOTENT_REPEAT"), "a repeated POST is not a no-op, got:\n" + out);
        assertTrue(out.contains("holds 4 resource(s)"), "two orders must exist, got:\n" + out);
        assertTrue(out.contains("1 of those were unintended repeats"),
                "the duplicate must be counted, got:\n" + out);
    }

    @Test
    void deletingTwiceEndsInTheSameStateWithADifferentStatus() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.delete("/orders/2");
            api.delete("/orders/2");
            api.report();
        });
        assertTrue(out.contains("204 No Content"), "the first DELETE must succeed, got:\n" + out);
        assertTrue(out.contains("404 Not Found"), "the second DELETE finds nothing, got:\n" + out);
        assertTrue(out.contains("IDEMPOTENT_REPEAT"),
                "the end state must be reported as unchanged, got:\n" + out);
        assertTrue(out.contains("holds 1 resource(s)"), "only one resource may remain, got:\n" + out);
    }

    @Test
    void aMergePatchOfAbsoluteValuesRepeatsSafely() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.patchMerge("/orders/1", Body.of("status", "paid"));
            api.patchMerge("/orders/1", Body.of("status", "paid"));
            api.report();
        });
        assertTrue(out.contains("IDEMPOTENT_REPEAT"), "a value patch must repeat safely, got:\n" + out);
        assertTrue(out.contains("item=tea, qty=2, status=paid"),
                "unmentioned fields must survive, got:\n" + out);
    }

    @Test
    void aRelativePatchAppliesTwice() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.patchIncrement("/orders/1", "qty", 1);
            api.patchIncrement("/orders/1", "qty", 1);
            api.report();
        });
        assertTrue(out.contains("RELATIVE_UPDATE"), "expected the relative-update event, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_EFFECT"), "a relative patch is not idempotent, got:\n" + out);
        assertTrue(out.contains("qty=4"), "the increment must have run twice, got:\n" + out);
    }

    @Test
    void anIdempotencyKeyTurnsARetriedPostIntoAReplay() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.dropNextResponse();
            api.postWithKey("/orders", "k-42", Body.of("item", "mugs").and("qty", "1"));
            api.retry();
            api.report();
        });
        assertTrue(out.contains("KEY_STORED"), "the first call must record the key, got:\n" + out);
        assertTrue(out.contains("KEY_REPLAYED"), "the retry must be recognized, got:\n" + out);
        assertFalse(out.contains("DUPLICATE_EFFECT"), "no second order may appear, got:\n" + out);
        assertTrue(out.contains("holds 3 resource(s)"), "exactly one order was created, got:\n" + out);
        assertTrue(out.contains("1 were recognized by an idempotency key"),
                "the replay must be counted, got:\n" + out);
    }

    @Test
    void anIdempotentMethodWithASideEffectingHandlerLeaks() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            Body target = Body.of("item", "tea").and("qty", "2").and("status", "shipped");
            api.dropNextResponse();
            api.putNotifying("/orders/1", target, "e-mail \"your order has shipped\"");
            api.retry();
            api.report();
        });
        assertTrue(out.contains("SIDE_EFFECT_FIRED"), "the first call must fire the effect, got:\n" + out);
        assertTrue(out.contains("SIDE_EFFECT_LEAK"), "the repeat must be reported, got:\n" + out);
        assertTrue(out.contains("side effects fired: 2"), "the effect ran twice, got:\n" + out);
        assertTrue(out.contains("holds 2 resource(s)"), "the resource itself is unchanged, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualIdempotency api = shop();
            api.get("/orders/1");
            api.head("/orders/2");
            api.get("/orders/99");
            api.put("/orders/1", Body.of("item", "tea").and("qty", "9"));
            api.patchMerge("/orders/1", Body.of("status", "paid"));
            api.patchIncrement("/orders/1", "qty", 2);
            api.dropNextResponse();
            api.post("/orders", Body.of("item", "mugs").and("qty", "1"));
            api.retry();
            api.postWithKey("/orders", "k-1", Body.of("item", "spoons").and("qty", "4"));
            api.putNotifying("/orders/2", Body.of("item", "cups").and("qty", "6"), "webhook");
            api.delete("/orders/1");
            api.delete("/orders/1");
            api.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
