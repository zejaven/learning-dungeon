package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSemaphoreTest {

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
    void emitsWaitAndGrantWhenPermitIsReleased() {
        String out = captureTrace(() -> {
            VisualSemaphore semaphore = new VisualSemaphore("gate", 1);
            semaphore.acquire("T1");
            semaphore.acquire("T2");
            semaphore.release("T1");
        });

        assertTrue(out.contains("SEMAPHORE_WAIT"),
                "expected a wait event, got:\n" + out);
        assertTrue(out.contains("SEMAPHORE_PERMIT_GRANTED"),
                "expected a handoff event, got:\n" + out);
    }

    @Test
    void tryAcquireFailureDoesNotJoinTheQueue() {
        String out = captureTrace(() -> {
            VisualSemaphore semaphore = new VisualSemaphore("gate", 1);
            semaphore.acquire("T1");

            assertEquals(0, semaphore.availablePermits());
            assertFalse(semaphore.tryAcquire("T2"));
            assertEquals(0, semaphore.queueLength());
        });

        assertTrue(out.contains("SEMAPHORE_TRY_ACQUIRE_FAILED"),
                "expected a tryAcquire failure event, got:\n" + out);
    }

    @Test
    void releaseWithoutOwnerCanOverRelease() {
        String out = captureTrace(() -> {
            VisualSemaphore semaphore = new VisualSemaphore("binaryGate", 1);
            semaphore.acquire("worker");
            semaphore.release("watchdog");
        });

        assertTrue(out.contains("SEMAPHORE_NO_OWNER_RELEASE"),
                "expected a no-owner release event, got:\n" + out);
        assertTrue(out.contains("SEMAPHORE_OVER_RELEASE"),
                "expected an over-release event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSemaphore semaphore = new VisualSemaphore("gate", 2);
            semaphore.acquire("T1");
            semaphore.tryAcquire("T2");
            semaphore.release("T1");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
