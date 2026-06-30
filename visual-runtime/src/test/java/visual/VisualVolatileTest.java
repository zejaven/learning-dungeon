package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualVolatileTest {

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
    void volatileReadEstablishesHappensBefore() {
        String out = captureTrace(() -> {
            VisualVolatile scene = new VisualVolatile("mailbox", true);
            scene.writeData("Writer", 42);
            scene.writeReady("Writer", true);
            scene.readReady("Reader");
            scene.readData("Reader");
        });

        assertTrue(out.contains("VOLATILE_WRITE"),
                "expected volatile write event, got:\n" + out);
        assertTrue(out.contains("HAPPENS_BEFORE_ESTABLISHED"),
                "expected happens-before event, got:\n" + out);
        assertTrue(out.contains("\"dataView\":42"),
                "expected reader data view to refresh, got:\n" + out);
    }

    @Test
    void plainReadyCanStayStale() {
        String out = captureTrace(() -> {
            VisualVolatile scene = new VisualVolatile("plain-mailbox", false);
            scene.writeData("Writer", 42);
            scene.writeReady("Writer", true);
            scene.readReady("Reader");
            scene.readData("Reader");
        });

        assertTrue(out.contains("PLAIN_READ_STALE"),
                "expected stale plain ready read, got:\n" + out);
        assertTrue(out.contains("PLAIN_DATA_READ_STALE"),
                "expected stale data read, got:\n" + out);
    }

    @Test
    void volatileCounterIncrementCanLoseUpdate() {
        String out = captureTrace(() -> {
            VisualVolatile scene = new VisualVolatile("counter", true);
            int t1 = scene.readCounter("T1");
            int t2 = scene.readCounter("T2");
            scene.writeCounter("T1", t1 + 1);
            scene.writeCounter("T2", t2 + 1);
        });

        assertTrue(out.contains("LOST_UPDATE"),
                "expected lost update event, got:\n" + out);
        assertTrue(out.contains("\"counterView\":1"),
                "expected thread-local counter view in trace, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualVolatile scene = new VisualVolatile("stop", true);
            scene.readReady("Worker");
            scene.writeReady("Main", true);
            scene.readReady("Worker");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
