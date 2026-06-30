package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualCompareAndSetTest {

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
    void emitsSuccessAndUpdatesValueWhenExpectedMatches() {
        int[] finalValue = new int[1];
        String out = captureTrace(() -> {
            VisualCompareAndSet slot = new VisualCompareAndSet("counter", 0);
            int expected = slot.read("T1");
            boolean updated = slot.compareAndSet("T1", expected, expected + 1);
            assertTrue(updated);
            finalValue[0] = slot.value();
        });

        assertTrue(out.contains("CAS_SUCCESS"), "expected CAS_SUCCESS, got:\n" + out);
        assertEquals(1, finalValue[0]);
    }

    @Test
    void emitsFailureWhenExpectedIsStale() {
        String out = captureTrace(() -> {
            VisualCompareAndSet slot = new VisualCompareAndSet("counter", 0);
            int stale = slot.read("T1");
            slot.compareAndSet("T2", 0, 1);
            boolean updated = slot.compareAndSet("T1", stale, stale + 1);
            assertTrue(!updated);
        });

        assertTrue(out.contains("CAS_FAILURE"), "expected CAS_FAILURE, got:\n" + out);
    }

    @Test
    void emitsRetryEventForFreshReadAfterFailure() {
        String out = captureTrace(() -> {
            VisualCompareAndSet slot = new VisualCompareAndSet("counter", 0);
            int expected = slot.read("T1");
            slot.compareAndSet("T2", 0, 1);
            if (!slot.compareAndSet("T1", expected, expected + 1)) {
                expected = slot.retryRead("T1");
                slot.compareAndSet("T1", expected, expected + 1);
            }
        });

        assertTrue(out.contains("CAS_RETRY"), "expected CAS_RETRY, got:\n" + out);
    }

    @Test
    void emitsAbaRiskWhenObservedValueChangedAwayAndBack() {
        String out = captureTrace(() -> {
            VisualCompareAndSet slot = new VisualCompareAndSet("state", 1);
            int expected = slot.read("T1");
            slot.compareAndSet("T2", 1, 2);
            slot.compareAndSet("T2", 2, 1);
            slot.compareAndSet("T1", expected, 3);
        });

        assertTrue(out.contains("CAS_ABA_RISK"), "expected CAS_ABA_RISK, got:\n" + out);
    }
}
