package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualThreadPoolTest {

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
    void emitsPerTaskThreadLifecycleEvents() {
        String out = captureTrace(() -> {
            VisualThreadPool demo = new VisualThreadPool("demo");

            demo.runWithNewThread("send-email", () -> {
            });
        });

        assertTrue(out.contains("THREAD_CREATED_PER_TASK"), "expected new thread creation, got:\n" + out);
        assertTrue(out.contains("THREAD_STARTED_PER_TASK"), "expected new thread start, got:\n" + out);
        assertTrue(out.contains("THREAD_TERMINATED_AFTER_TASK"), "expected new thread termination, got:\n" + out);
    }

    @Test
    void emitsPoolSubmissionQueueAndReuseEvents() {
        String out = captureTrace(() -> {
            VisualThreadPool demo = new VisualThreadPool("demo");
            var pool = demo.fixedPool("apiPool", 1, 2);

            pool.submit("request-1", () -> {
            });
            pool.submit("request-2", () -> {
            });
            pool.completeOne();
        });

        assertTrue(out.contains("THREAD_POOL_CREATED"), "expected pool creation, got:\n" + out);
        assertTrue(out.contains("POOL_TASK_SUBMITTED"), "expected task submission, got:\n" + out);
        assertTrue(out.contains("POOL_TASK_ASSIGNED"), "expected assignment, got:\n" + out);
        assertTrue(out.contains("POOL_TASK_QUEUED"), "expected queueing, got:\n" + out);
        assertTrue(out.contains("POOL_TASK_COMPLETED"), "expected completion, got:\n" + out);
        assertTrue(out.contains("POOL_WORKER_REUSED"), "expected worker reuse, got:\n" + out);
    }

    @Test
    void emitsRejectionWhenQueueIsFull() {
        String out = captureTrace(() -> {
            VisualThreadPool demo = new VisualThreadPool("demo");
            var pool = demo.fixedPool("smallPool", 1, 1);

            pool.submit("task-1", () -> {
            });
            pool.submit("task-2", () -> {
            });
            pool.submit("task-3", () -> {
            });
        });

        assertTrue(out.contains("POOL_TASK_REJECTED"), "expected rejected task, got:\n" + out);
        assertTrue(out.contains("QUEUE_FULL"), "expected queue-full reason in state, got:\n" + out);
    }

    @Test
    void rejectsAfterShutdownButCompletesExistingWork() {
        String out = captureTrace(() -> {
            VisualThreadPool demo = new VisualThreadPool("demo");
            var pool = demo.fixedPool("workerPool", 1, 1);

            pool.submit("accepted", () -> {
            });
            pool.shutdown();
            pool.submit("late", () -> {
            });
            pool.completeAll();
        });

        assertTrue(out.contains("THREAD_POOL_SHUTDOWN"), "expected shutdown event, got:\n" + out);
        assertTrue(out.contains("POOL_TASK_REJECTED"), "expected late rejection, got:\n" + out);
        assertTrue(out.contains("SHUTDOWN"), "expected shutdown reason in state, got:\n" + out);
        assertTrue(out.contains("POOL_TASK_COMPLETED"), "expected accepted task to complete, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualThreadPool demo = new VisualThreadPool("demo");
            var pool = demo.fixedPool("apiPool", 1, 1);

            pool.submit("request", () -> {
            });
            pool.completeAll();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
