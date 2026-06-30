package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualConcurrentListTest {

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
    void modifyingDuringIterationThrowsConcurrentModification() {
        String out = captureTrace(() -> {
            VisualConcurrentList list = new VisualConcurrentList("tasks", VisualConcurrentList.FAIL_FAST);
            list.add("a");
            list.add("b");
            list.iterator();
            list.next();
            list.add("c");
            list.next();
        });

        assertTrue(out.contains("LIST_ADD"),
                "expected a structural add event, got:\n" + out);
        assertTrue(out.contains("CONCURRENT_MODIFICATION"),
                "expected a ConcurrentModificationException event, got:\n" + out);
    }

    @Test
    void iteratorRemoveStaysValid() {
        String out = captureTrace(() -> {
            VisualConcurrentList list = new VisualConcurrentList("tasks");
            list.add("a");
            list.add("b");
            list.iterator();
            list.next();
            list.iteratorRemove();
            list.next();
        });

        assertTrue(out.contains("ITERATOR_REMOVE"),
                "expected a safe iterator.remove() event, got:\n" + out);
        assertTrue(!out.contains("CONCURRENT_MODIFICATION"),
                "iterator.remove() must not trigger a CME, got:\n" + out);
    }

    @Test
    void copyOnWriteKeepsSnapshotStable() {
        String out = captureTrace(() -> {
            VisualConcurrentList list = new VisualConcurrentList("shared", VisualConcurrentList.COPY_ON_WRITE);
            list.add("Writer", "a");
            list.add("Writer", "b");
            list.iterator("Reader");
            list.next("Reader");
            list.add("Writer", "c");
            list.next("Reader");
        });

        assertTrue(out.contains("COW_WRITE_COPY"),
                "expected a copy-on-write event, got:\n" + out);
        assertTrue(out.contains("COW_SNAPSHOT_READ"),
                "expected the reader to keep reading its snapshot, got:\n" + out);
        assertTrue(!out.contains("CONCURRENT_MODIFICATION"),
                "CopyOnWriteArrayList must never throw a CME, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualConcurrentList list = new VisualConcurrentList("tasks");
            list.add("a");
            list.iterator();
            list.next();
            list.next();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
