package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualMonitorTest {

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
    void waitReleasesMonitorAndMovesThreadToWaitSet() {
        String out = captureTrace(() -> {
            VisualMonitor monitor = new VisualMonitor("mailbox");
            monitor.enter("consumer");
            monitor.waitOnCondition("consumer");
            monitor.enter("producer");
        });

        assertTrue(out.contains("\"event\":\"MONITOR_WAIT_RELEASED\""),
                "expected wait release event, got:\n" + out);
        assertTrue(out.contains("\"waitSet\":[{\"thread\":\"consumer\"}]"),
                "expected consumer in wait set, got:\n" + out);
        assertTrue(out.contains("\"owner\":\"producer\""),
                "expected another thread to enter after wait released the monitor, got:\n" + out);
    }

    @Test
    void notifyDoesNotReleaseMonitorUntilExit() {
        String out = captureTrace(() -> {
            VisualMonitor monitor = new VisualMonitor("mailbox");
            monitor.enter("consumer");
            monitor.waitOnCondition("consumer");
            monitor.enter("producer");
            monitor.setConditionReady("producer", true);
            monitor.notifyOne("producer");
            monitor.exit("producer");
        });

        String notifyLine = lineWith(out, "MONITOR_NOTIFY");
        assertTrue(notifyLine.contains("\"owner\":\"producer\""),
                "notify() should leave the notifier as owner, got:\n" + notifyLine);
        assertTrue(notifyLine.contains("\"entrySet\":[{\"thread\":\"consumer\",\"reason\":\"notified\"}]"),
                "expected notified consumer to wait for re-acquire, got:\n" + notifyLine);
        assertTrue(out.contains("\"event\":\"MONITOR_REACQUIRED\""),
                "expected consumer to re-acquire after producer exits, got:\n" + out);
    }

    @Test
    void notifyAllMovesEveryWaiterTowardReacquisition() {
        String out = captureTrace(() -> {
            VisualMonitor monitor = new VisualMonitor("mailbox");
            monitor.enter("consumerA");
            monitor.waitOnCondition("consumerA");
            monitor.enter("consumerB");
            monitor.waitOnCondition("consumerB");
            monitor.enter("producer");
            monitor.notifyAllWaiters("producer");
        });

        String notifyAllLine = lineWith(out, "MONITOR_NOTIFY_ALL");
        assertTrue(notifyAllLine.contains("\"thread\":\"consumerA\",\"reason\":\"notified\""),
                "expected consumerA in entry set, got:\n" + notifyAllLine);
        assertTrue(notifyAllLine.contains("\"thread\":\"consumerB\",\"reason\":\"notified\""),
                "expected consumerB in entry set, got:\n" + notifyAllLine);
    }

    @Test
    void spuriousWakeupStillRequiresConditionRecheck() {
        String out = captureTrace(() -> {
            VisualMonitor monitor = new VisualMonitor("mailbox");
            monitor.enter("consumer");
            monitor.checkCondition("consumer");
            monitor.waitOnCondition("consumer");
            monitor.spuriousWakeup("consumer");
            monitor.checkCondition("consumer");
        });

        assertTrue(out.contains("\"event\":\"MONITOR_SPURIOUS_WAKEUP\""),
                "expected spurious wakeup event, got:\n" + out);
        assertTrue(out.contains("\"event\":\"CONDITION_CHECK_FALSE\""),
                "expected a false condition check, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualMonitor monitor = new VisualMonitor("mailbox");
            monitor.enter("consumer");
            monitor.waitOnCondition("consumer");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }

    private String lineWith(String out, String event) {
        return out.lines()
                .filter(line -> line.contains("\"event\":\"" + event + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing event " + event + " in:\n" + out));
    }
}
