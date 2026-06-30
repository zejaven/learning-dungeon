package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLockAlternativesTest {

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
    void reentrantLockShowsReentryAndTryLockFailure() {
        String out = captureTrace(() -> {
            VisualLockAlternatives.Reentrant lock =
                    VisualLockAlternatives.reentrantLock("accountLock");
            lock.lock("T1");
            lock.lock("T1");
            assertFalse(lock.tryLock("T2"));
            assertEquals(2, lock.holdCount());
        });

        assertTrue(out.contains("REENTRANT_LOCK_REENTERED"),
                "expected reentrant event, got:\n" + out);
        assertTrue(out.contains("REENTRANT_TRY_LOCK_FAILED"),
                "expected failed tryLock event, got:\n" + out);
    }

    @Test
    void readWriteLockSharesReadsAndQueuesWriter() {
        String out = captureTrace(() -> {
            VisualLockAlternatives.ReadWrite lock =
                    VisualLockAlternatives.readWriteLock("catalogLock");
            lock.readLock("reader-1");
            lock.readLock("reader-2");
            lock.writeLock("writer");
            lock.unlockRead("reader-1");
            lock.unlockRead("reader-2");
        });

        assertTrue(out.contains("READWRITE_READ_SHARED"),
                "expected shared read event, got:\n" + out);
        assertTrue(out.contains("READWRITE_WRITE_WAITING"),
                "expected writer wait event, got:\n" + out);
        assertTrue(out.contains("READWRITE_WRITE_ACQUIRED"),
                "expected writer grant event, got:\n" + out);
    }

    @Test
    void stampedLockInvalidatesOptimisticReadAfterWrite() {
        String out = captureTrace(() -> {
            VisualLockAlternatives.Stamped lock =
                    VisualLockAlternatives.stampedLock("pointLock");
            long stamp = lock.tryOptimisticRead("reader");
            lock.writeLock("writer");
            lock.unlockWrite("writer");
            assertFalse(lock.validate("reader", stamp));
        });

        assertTrue(out.contains("STAMPED_OPTIMISTIC_READ"),
                "expected optimistic read event, got:\n" + out);
        assertTrue(out.contains("STAMPED_VALIDATE_FAILED"),
                "expected failed validation event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualLockAlternatives.Reentrant lock =
                    VisualLockAlternatives.reentrantLock("guard");
            lock.lock("T1");
            lock.tryLock("T2");
            lock.unlock("T1");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
