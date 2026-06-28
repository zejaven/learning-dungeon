package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualConcurrentMapTest {

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
    void synchronizedMapBlocksASecondThreadEvenForAReadOnAnotherKey() {
        String out = captureTrace(() -> {
            VisualConcurrentMap map = VisualConcurrentMap.synchronizedMap("sessions");
            map.lock("T1", "put(alice)");
            map.putLocked("T1", "alice", "online");
            map.lock("T2", "get(bob)"); // global monitor busy -> blocked
        });
        assertTrue(out.contains("SYNC_BLOCKED"),
                "expected the second thread to block on the one monitor, got:\n" + out);
    }

    @Test
    void concurrentMapLocksDifferentBinsInParallel() {
        // alice -> bin 1, bob -> bin 4 : different bins, both lock at once.
        assertEquals(1, VisualConcurrentMap.bin("alice"));
        assertEquals(4, VisualConcurrentMap.bin("bob"));
        String out = captureTrace(() -> {
            VisualConcurrentMap map = VisualConcurrentMap.concurrentHashMap("sessions");
            map.lockBin("T1", "alice");
            map.lockBin("T2", "bob");
        });
        assertTrue(out.contains("CHM_BIN_LOCK_ACQUIRED"),
                "expected per-bin lock acquisitions, got:\n" + out);
        assertTrue(!out.contains("CHM_BIN_BLOCKED"),
                "different bins must not block each other, got:\n" + out);
    }

    @Test
    void concurrentMapBlocksWritersThatShareABin() {
        // alice and carol both hash to bin 1.
        assertEquals(VisualConcurrentMap.bin("alice"), VisualConcurrentMap.bin("carol"));
        String out = captureTrace(() -> {
            VisualConcurrentMap map = VisualConcurrentMap.concurrentHashMap("sessions");
            map.lockBin("T1", "alice");
            map.lockBin("T2", "carol"); // same bin -> blocked
        });
        assertTrue(out.contains("CHM_BIN_BLOCKED"),
                "expected same-bin contention, got:\n" + out);
    }

    @Test
    void concurrentReadTakesNoLockEvenWhileABinIsBeingWritten() {
        String out = captureTrace(() -> {
            VisualConcurrentMap map = VisualConcurrentMap.concurrentHashMap("sessions");
            map.lockBin("T1", "alice");
            map.putInBin("T1", "alice", "online");
            map.get("T2", "alice"); // lock-free read while bin 1 is locked
        });
        assertTrue(out.contains("CHM_GET"),
                "expected a lock-free read event, got:\n" + out);
    }

    @Test
    void computeIfAbsentIsAtomic() {
        String out = captureTrace(() -> {
            VisualConcurrentMap map = VisualConcurrentMap.concurrentHashMap("sessions");
            map.computeIfAbsent("T1", "alice", "online");
            map.computeIfAbsent("T2", "alice", "elsewhere");
        });
        assertTrue(out.contains("CHM_ATOMIC"),
                "expected an atomic compound operation event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualConcurrentMap map = VisualConcurrentMap.concurrentHashMap("sessions");
            map.lockBin("T1", "alice");
            map.putInBin("T1", "alice", "online");
            map.unlockBin("T1", "alice");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
