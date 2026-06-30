package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualThreadStopTest {

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
    void emitsCooperativeStopEvents() {
        String out = captureTrace(() -> {
            VisualThreadStop demo = new VisualThreadStop("demo");
            demo.createWorker("printer");
            demo.start("printer");
            demo.work("printer", 2);
            demo.requestStop("printer");
            demo.observeStopRequest("printer");
            demo.exit("printer");
            demo.join("printer");
        });

        assertTrue(out.contains("THREAD_STARTED"), "expected start event, got:\n" + out);
        assertTrue(out.contains("STOP_REQUESTED"), "expected stop request, got:\n" + out);
        assertTrue(out.contains("STOP_REQUEST_OBSERVED"), "expected observed stop flag, got:\n" + out);
        assertTrue(out.contains("THREAD_EXITED"), "expected exit event, got:\n" + out);
        assertTrue(out.contains("JOIN_COMPLETED"), "expected join event, got:\n" + out);
    }

    @Test
    void emitsInterruptForBlockedWorker() {
        String out = captureTrace(() -> {
            VisualThreadStop demo = new VisualThreadStop("demo");
            demo.createWorker("loader");
            demo.start("loader");
            demo.block("loader", "sleep()");
            demo.interrupt("loader");
            demo.handleInterruptedException("loader");
            demo.restoreInterruptStatus("loader");
            demo.exit("loader");
        });

        assertTrue(out.contains("WORKER_BLOCKED"), "expected blocked event, got:\n" + out);
        assertTrue(out.contains("THREAD_INTERRUPTED"), "expected interrupt event, got:\n" + out);
        assertTrue(out.contains("INTERRUPT_OBSERVED"), "expected InterruptedException handling, got:\n" + out);
        assertTrue(out.contains("INTERRUPT_RESTORED"), "expected interrupt restoration, got:\n" + out);
        assertTrue(out.contains("\"interruptStatus\":true"), "expected restored interrupt status, got:\n" + out);
    }

    @Test
    void emitsInterruptStatusCheckForRunningLoop() {
        String out = captureTrace(() -> {
            VisualThreadStop demo = new VisualThreadStop("demo");
            demo.createWorker("poller");
            demo.start("poller");
            demo.work("poller", 1);
            demo.interrupt("poller");
            demo.observeInterruptStatus("poller");
            demo.exit("poller");
        });

        assertTrue(out.contains("INTERRUPT_STATUS_OBSERVED"), "expected interrupt status check, got:\n" + out);
        assertTrue(out.contains("\"lastObservation\":\"interrupt status\""), "expected interrupt observation, got:\n" + out);
    }

    @Test
    void emitsUnsafeStopRejection() {
        String out = captureTrace(() -> {
            VisualThreadStop demo = new VisualThreadStop("demo");
            demo.createWorker("writer");
            demo.start("writer");
            demo.unsafeStopAttempt("writer");
        });

        assertTrue(out.contains("UNSAFE_STOP_REJECTED"), "expected Thread.stop rejection, got:\n" + out);
        assertTrue(out.contains("\"unsafeStopAttempts\":1"), "expected unsafe stop counter, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualThreadStop demo = new VisualThreadStop("demo");
            demo.createWorker("quiet");
            demo.start("quiet");
            demo.requestStop("quiet");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
