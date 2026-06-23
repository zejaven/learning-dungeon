package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualContextSwitchTest {

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
    void emitsSaveRestoreAndSwitchForExpiredTimeSlice() {
        String out = captureTrace(() -> {
            VisualContextSwitch scheduler = new VisualContextSwitch("demo");
            scheduler.addThread("request-A");
            scheduler.addThread("request-B");

            scheduler.dispatchNext();
            scheduler.runInstructions(3);
            scheduler.expireTimeSlice();
        });

        assertTrue(out.contains("CONTEXT_SAVED"), "expected context save, got:\n" + out);
        assertTrue(out.contains("CONTEXT_RESTORED"), "expected context restore, got:\n" + out);
        assertTrue(out.contains("CONTEXT_SWITCHED"), "expected context switch, got:\n" + out);
        assertTrue(out.contains("\"runningThread\":\"request-B\""), "expected request-B to run, got:\n" + out);
    }

    @Test
    void emitsBlockingAndWakeEvents() {
        String out = captureTrace(() -> {
            VisualContextSwitch scheduler = new VisualContextSwitch("io-demo");
            scheduler.addThread("web-request");
            scheduler.addThread("metrics");

            scheduler.dispatchNext();
            scheduler.runInstructions(2);
            scheduler.blockForIo("database");
            scheduler.wake("web-request");
        });

        assertTrue(out.contains("THREAD_BLOCKED"), "expected blocking event, got:\n" + out);
        assertTrue(out.contains("THREAD_WOKE"), "expected wake event, got:\n" + out);
        assertTrue(out.contains("\"state\":\"WAITING\""), "expected waiting state, got:\n" + out);
    }

    @Test
    void recordsOverheadTicksAcrossSwitches() {
        String out = captureTrace(() -> {
            VisualContextSwitch scheduler = new VisualContextSwitch("overhead-demo");
            scheduler.addThread("parser");
            scheduler.addThread("compressor");

            scheduler.dispatchNext();
            scheduler.runInstructions(1);
            scheduler.expireTimeSlice();
            scheduler.runInstructions(1);
            scheduler.expireTimeSlice();
        });

        assertTrue(out.contains("CONTEXT_SWITCHED"), "expected switch event, got:\n" + out);
        assertTrue(out.contains("\"overheadTicks\""), "expected overhead metric, got:\n" + out);
        assertTrue(out.contains("\"contextSwitches\":2"), "expected two real switches, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualContextSwitch scheduler = new VisualContextSwitch("prefix-demo");
            scheduler.addThread("worker");
            scheduler.dispatchNext();
            scheduler.runInstructions(1);
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
