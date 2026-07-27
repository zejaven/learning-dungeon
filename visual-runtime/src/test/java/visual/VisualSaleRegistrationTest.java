package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSaleRegistrationTest {

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
    void creatingADeviceEmitsCreated() {
        String out = captureTrace(() -> new VisualSaleRegistration("pos-7"));
        assertTrue(out.contains("POS_CREATED"), "expected a creation event, got:\n" + out);
    }

    @Test
    void saleIsQueuedLocallyBeforeAnySend() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.recordSale("sale-1", "coffee", 250);
        });
        assertTrue(out.contains("SALE_QUEUED"), "expected a queued event, got:\n" + out);
        assertFalse(out.contains("SALE_APPLIED"), "recording must not touch the server, got:\n" + out);
    }

    @Test
    void lostRequestChangesNothingOnTheServerAndTheRetryApplies() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.recordSale("sale-1", "coffee", 250);
            pos.attemptRequestLost("sale-1");
            boolean applied = pos.attemptDelivered("sale-1");
            assertTrue(applied, "a request that never arrived leaves the key unused");
        });
        assertTrue(out.contains("REQUEST_LOST"), "expected a lost-request event, got:\n" + out);
        assertTrue(out.contains("total is now 250"), "the retry must register the sale, got:\n" + out);
        assertFalse(out.contains("total is now 500"), "the sale must apply once, got:\n" + out);
    }

    @Test
    void lostResponseIsDeduplicatedByTheSameKey() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.recordSale("sale-1", "coffee", 250);
            pos.attemptResponseLost("sale-1");
            boolean applied = pos.attemptDelivered("sale-1");
            assertFalse(applied, "the retry of a key the server already has must not apply again");
        });
        assertTrue(out.contains("RESPONSE_LOST"), "expected a lost-response event, got:\n" + out);
        assertTrue(out.contains("DUPLICATE_IGNORED"), "expected a dedup event, got:\n" + out);
        assertFalse(out.contains("total is now 500"), "the sale must apply once, got:\n" + out);
    }

    @Test
    void aNewKeyPerRetryDoubleCharges() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.recordSale("sale-a1", "coffee", 250);
            pos.attemptResponseLost("sale-a1");
            // The mistake: the retry is recorded under a freshly generated key.
            pos.recordSale("sale-a2", "coffee", 250);
            boolean applied = pos.attemptDelivered("sale-a2");
            assertTrue(applied, "a different key is a different sale for the server");
        });
        assertTrue(out.contains("DOUBLE_CHARGE_DETECTED"),
                "expected the double-charge signal, got:\n" + out);
        assertTrue(out.contains("total is now 500"), "the customer is charged twice, got:\n" + out);
    }

    @Test
    void offlineSalesAreQueuedAndDrainedOnReconnect() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.goOffline();
            pos.recordSale("sale-1", "coffee", 250);
            pos.recordSale("sale-2", "bagel", 180);
            pos.reconnect();
        });
        assertTrue(out.contains("CONNECTION_LOST"), "expected an offline event, got:\n" + out);
        assertTrue(out.contains("CONNECTION_RESTORED"), "expected a reconnect event, got:\n" + out);
        assertTrue(out.contains("QUEUE_DRAINED"), "expected a drain event, got:\n" + out);
        assertTrue(out.contains("total is now 430"), "both queued sales must sync, got:\n" + out);
    }

    @Test
    void drainingIsSafeWhenTheServerAlreadyHasTheSale() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.recordSale("sale-1", "coffee", 250);
            pos.attemptResponseLost("sale-1");   // registered on the server, unknown to the client
            pos.goOffline();
            pos.reconnect();                     // drains the same key again
        });
        assertTrue(out.contains("DUPLICATE_IGNORED"), "the drain must be deduplicated, got:\n" + out);
        assertFalse(out.contains("total is now 500"), "the sale must apply once, got:\n" + out);
    }

    @Test
    void retryDelayGrowsExponentiallyAndIsCapped() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.recordSale("sale-1", "coffee", 250);
            for (int i = 0; i < 8; i++) {
                pos.attemptRequestLost("sale-1");
            }
        });
        assertTrue(out.contains("retry in 200 ms"), "first backoff must be the base delay, got:\n" + out);
        assertTrue(out.contains("retry in 400 ms"), "backoff must double, got:\n" + out);
        assertTrue(out.contains("retry in 6400 ms"), "backoff must reach the cap, got:\n" + out);
        assertFalse(out.contains("retry in 12800 ms"), "backoff must be capped, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSaleRegistration pos = new VisualSaleRegistration("pos-7");
            pos.recordSale("sale-1", "coffee", 250);
            pos.attemptRequestLost("sale-1");
            pos.attemptResponseLost("sale-1");
            pos.attemptDelivered("sale-1");
            pos.goOffline();
            pos.recordSale("sale-2", "bagel", 180);
            pos.reconnect();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
