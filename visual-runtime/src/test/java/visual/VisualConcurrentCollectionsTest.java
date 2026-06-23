package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualConcurrentCollectionsTest {

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
    void emitsBlockedWhenSynchronizedMonitorIsOwned() {
        String out = captureTrace(() -> {
            VisualConcurrentCollections list = VisualConcurrentCollections.synchronizedList("syncList", "A");
            list.beginSynchronizedOperation("T1", "add(B)");
            list.beginSynchronizedOperation("T2", "add(C)");
        });

        assertTrue(out.contains("SYNC_THREAD_BLOCKED"),
                "expected blocked event, got:\n" + out);
    }

    @Test
    void emitsFailFastWhenSynchronizedCollectionChangesDuringIteration() {
        String out = captureTrace(() -> {
            VisualConcurrentCollections list = VisualConcurrentCollections.synchronizedList("syncList", "A", "B");
            list.createFailFastIterator("it", "T1");
            list.addWithSynchronizedLock("T2", "C");
            list.next("it");
        });

        assertTrue(out.contains("FAIL_FAST_ITERATOR_INVALIDATED"),
                "expected fail-fast event, got:\n" + out);
    }

    @Test
    void putIfAbsentKeepsExistingValueAtomically() {
        VisualConcurrentCollections map = VisualConcurrentCollections.concurrentMap("dedupe");

        assertTrue(map.putIfAbsent("T1", "order-42", "processing"));
        assertFalse(map.putIfAbsent("T2", "order-42", "duplicate"));
        assertEquals("processing", map.getConcurrent("T3", "order-42"));
    }

    @Test
    void weakIteratorContinuesAfterConcurrentWrite() {
        String out = captureTrace(() -> {
            VisualConcurrentCollections map = VisualConcurrentCollections.concurrentMap("sessions");
            map.putConcurrent("main", "A", "online");
            map.putConcurrent("main", "B", "idle");
            map.createWeakIterator("scan", "T1");
            map.next("scan");
            map.putConcurrent("T2", "C", "online");
            map.next("scan");
        });

        assertTrue(out.contains("WEAK_ITERATOR_CONTINUES"),
                "expected weak iterator event, got:\n" + out);
    }

    @Test
    void snapshotIteratorReadsOldCopyAfterCopyOnWriteMutation() {
        String out = captureTrace(() -> {
            VisualConcurrentCollections list = VisualConcurrentCollections.copyOnWriteList("listeners", "audit", "email");
            list.createSnapshotIterator("notify", "T1");
            list.addCopyOnWrite("T2", "metrics");
            list.next("notify");
        });

        assertTrue(out.contains("COPY_ON_WRITE_WRITE"),
                "expected copy-on-write event, got:\n" + out);
        assertTrue(out.contains("SNAPSHOT_ITERATOR_READ"),
                "expected snapshot read event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualConcurrentCollections map = VisualConcurrentCollections.concurrentMap("sessions");
            map.putConcurrent("T1", "alice", "online");
            map.getConcurrent("T2", "alice");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
