package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualCriticalSectionTest {

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
    void emitsWaitingAndGrantEventsWhenLockIsBusy() {
        String out = captureTrace(() -> {
            VisualCriticalSection section = new VisualCriticalSection("counter", 0);
            section.enter("T1");
            section.enter("T2");
            section.exit("T1");
        });

        assertTrue(out.contains("THREAD_WAITING"),
                "expected waiting event, got:\n" + out);
        assertTrue(out.contains("The lock is handed to waiting thread T2"),
                "expected lock handoff description, got:\n" + out);
    }

    @Test
    void protectsReadModifyWriteWhenThreadOwnsTheLock() {
        VisualCriticalSection section = new VisualCriticalSection("counter", 0);
        section.enter("T1");
        int value = section.read("T1");
        section.write("T1", value + 1);
        section.exit("T1");

        assertEquals(1, section.value());
    }

    @Test
    void emitsLostUpdateForStaleUnprotectedWrite() {
        String out = captureTrace(() -> {
            VisualCriticalSection section = new VisualCriticalSection("counter", 0);
            int t1 = section.unsafeRead("T1");
            int t2 = section.unsafeRead("T2");
            section.unsafeWrite("T1", t1 + 1);
            section.unsafeWrite("T2", t2 + 1);
        });

        assertTrue(out.contains("LOST_UPDATE"),
                "expected lost update event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualCriticalSection section = new VisualCriticalSection("counter", 0);
            section.enter("T1");
            section.read("T1");
            section.write("T1", 1);
            section.exit("T1");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
