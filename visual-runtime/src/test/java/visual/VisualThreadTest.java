package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualThreadTest {

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
    void emitsRunnableThreadStartAndExecutionEvents() {
        String out = captureTrace(() -> {
            VisualThread demo = new VisualThread("demo");
            Runnable job = demo.runnable("packOrder", () -> {
            });
            Thread worker = demo.thread("worker-1", job);

            demo.start(worker);
        });

        assertTrue(out.contains("RUNNABLE_CREATED"), "expected Runnable creation, got:\n" + out);
        assertTrue(out.contains("THREAD_CREATED_WITH_RUNNABLE"), "expected Thread creation, got:\n" + out);
        assertTrue(out.contains("THREAD_STARTED"), "expected start() event, got:\n" + out);
        assertTrue(out.contains("RUNNABLE_EXECUTED"), "expected Runnable execution, got:\n" + out);
        assertTrue(out.contains("THREAD_TERMINATED"), "expected termination, got:\n" + out);
    }

    @Test
    void emitsSubclassThreadEvent() {
        String out = captureTrace(() -> {
            VisualThread demo = new VisualThread("demo");
            Thread worker = demo.threadSubclass("legacy-worker", () -> {
            });

            demo.start(worker);
        });

        assertTrue(out.contains("THREAD_SUBCLASS_CREATED"), "expected subclass creation, got:\n" + out);
        assertTrue(out.contains("THREAD_RUN_EXECUTED"), "expected overridden run() execution, got:\n" + out);
    }

    @Test
    void emitsDirectRunWithoutStartEvent() {
        String out = captureTrace(() -> {
            VisualThread demo = new VisualThread("demo");
            Runnable job = demo.runnable("inlineJob", () -> {
            });
            Thread worker = demo.thread("not-started-worker", job);

            demo.callRunDirectly(worker);
        });

        assertTrue(out.contains("THREAD_RUN_CALLED_DIRECTLY"), "expected direct run() event, got:\n" + out);
        assertTrue(out.contains("RUNNABLE_EXECUTED"), "expected Runnable execution, got:\n" + out);
        assertFalse(out.contains("THREAD_STARTED"), "run() should not emit start(), got:\n" + out);
    }

    @Test
    void emitsRunnableReusedForTwoThreadObjects() {
        String out = captureTrace(() -> {
            VisualThread demo = new VisualThread("demo");
            Runnable job = demo.runnable("dailyJob", () -> {
            });

            demo.thread("worker-a", job);
            demo.thread("worker-b", job);
        });

        assertTrue(out.contains("RUNNABLE_REUSED"), "expected reused Runnable event, got:\n" + out);
    }

    @Test
    void everyModelLineIsPrefixedWhenTaskIsQuiet() {
        String out = captureTrace(() -> {
            VisualThread demo = new VisualThread("demo");
            Runnable job = demo.runnable("quietJob", () -> {
            });
            Thread worker = demo.thread("quiet-worker", job);

            demo.start(worker);
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
