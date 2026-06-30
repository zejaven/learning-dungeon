package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHappensBeforeTest {

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
    void plainReadCanStayStaleWithoutHappensBefore() {
        String out = captureTrace(() -> {
            VisualHappensBefore hb = new VisualHappensBefore("plain");
            hb.writePlain("Writer", "note", "ready");
            hb.readPlain("Reader", "note");
        });

        assertTrue(out.contains("PLAIN_READ_STALE"),
                "expected stale plain read event, got:\n" + out);
        assertTrue(out.contains("\"value\":\"unset\""),
                "expected reader local value to remain unset, got:\n" + out);
    }

    @Test
    void volatileReadCreatesHappensBeforeAndRefreshesPlainData() {
        String out = captureTrace(() -> {
            VisualHappensBefore hb = new VisualHappensBefore("volatile");
            hb.writePlain("Writer", "payload", "invoice-42");
            hb.writeVolatile("Writer", "ready", true);
            hb.readVolatile("Reader", "ready");
            hb.readPlain("Reader", "payload");
        });

        assertTrue(out.contains("VOLATILE_HAPPENS_BEFORE"),
                "expected volatile happens-before event, got:\n" + out);
        assertTrue(out.contains("TRANSITIVE_VISIBILITY"),
                "expected transitive visibility event, got:\n" + out);
        assertTrue(out.contains("\"payload\",\"value\":\"invoice-42\""),
                "expected payload to be visible in local values, got:\n" + out);
    }

    @Test
    void monitorReleaseAndAcquireCreateHappensBefore() {
        String out = captureTrace(() -> {
            VisualHappensBefore hb = new VisualHappensBefore("monitor");
            hb.lock("Producer", "mailboxLock");
            hb.writePlain("Producer", "message", "approved");
            hb.unlock("Producer", "mailboxLock");
            hb.lock("Consumer", "mailboxLock");
            hb.readPlain("Consumer", "message");
        });

        assertTrue(out.contains("MONITOR_HAPPENS_BEFORE"),
                "expected monitor happens-before event, got:\n" + out);
        assertTrue(out.contains("\"kind\":\"MONITOR\""),
                "expected monitor edge in state, got:\n" + out);
    }

    @Test
    void threadStartAndJoinCreateEdges() {
        String out = captureTrace(() -> {
            VisualHappensBefore hb = new VisualHappensBefore("threads");
            hb.writePlain("main", "config", "loaded");
            hb.startThread("main", "Worker");
            hb.readPlain("Worker", "config");
            hb.writePlain("Worker", "result", "done");
            hb.finishThread("Worker");
            hb.joinThread("main", "Worker");
            hb.readPlain("main", "result");
        });

        assertTrue(out.contains("THREAD_START_EDGE"),
                "expected Thread.start happens-before event, got:\n" + out);
        assertTrue(out.contains("THREAD_JOIN_EDGE"),
                "expected Thread.join happens-before event, got:\n" + out);
        assertTrue(out.contains("\"kind\":\"THREAD_JOIN\""),
                "expected join edge in state, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualHappensBefore hb = new VisualHappensBefore("prefix");
            hb.writePlain("Writer", "note", "ready");
            hb.readPlain("Reader", "note");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
