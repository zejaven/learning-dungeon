package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualTaskBatchTest {

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
    void emitsLimitedExecutorAndQueueEvents() {
        String out = captureTrace(() -> {
            VisualTaskBatch scene = new VisualTaskBatch("limited");
            var executor = scene.fixedExecutor("workerPool", 1);

            executor.submit("task-1");
            executor.submit("task-2");
        });

        assertTrue(out.contains("LIMITED_EXECUTOR_CREATED"), "expected executor creation, got:\n" + out);
        assertTrue(out.contains("TASK_SUBMITTED"), "expected task submission, got:\n" + out);
        assertTrue(out.contains("TASK_STARTED"), "expected task start, got:\n" + out);
        assertTrue(out.contains("TASK_QUEUED"), "expected queueing, got:\n" + out);
    }

    @Test
    void emitsLatchAwaitCountdownAndReleaseEvents() {
        String out = captureTrace(() -> {
            VisualTaskBatch scene = new VisualTaskBatch("latch");
            var executor = scene.fixedExecutor("workerPool", 2);
            var latch = scene.countDownLatch("done", 2);

            executor.submit("task-1", () -> latch.countDown("task-1"));
            executor.submit("task-2", () -> latch.countDown("task-2"));
            latch.await();
            executor.completeAll();
        });

        assertTrue(out.contains("LATCH_CREATED"), "expected latch creation, got:\n" + out);
        assertTrue(out.contains("MAIN_AWAITING_LATCH"), "expected main await, got:\n" + out);
        assertTrue(out.contains("LATCH_COUNTED_DOWN"), "expected countDown, got:\n" + out);
        assertTrue(out.contains("MAIN_RELEASED_BY_LATCH"), "expected latch release, got:\n" + out);
    }

    @Test
    void emitsInvokeAllCompletionEvents() {
        String out = captureTrace(() -> {
            VisualTaskBatch scene = new VisualTaskBatch("invoke-all");
            var executor = scene.fixedExecutor("workerPool", 2);

            executor.invokeAll(List.of("load-user", "load-orders", "load-score"));
        });

        assertTrue(out.contains("INVOKE_ALL_CALLED"), "expected invokeAll call, got:\n" + out);
        assertTrue(out.contains("TASK_QUEUED"), "expected limited pool queueing, got:\n" + out);
        assertTrue(out.contains("INVOKE_ALL_COMPLETED"), "expected invokeAll completion, got:\n" + out);
    }

    @Test
    void emitsFutureWaitAndReturnEvents() {
        String out = captureTrace(() -> {
            VisualTaskBatch scene = new VisualTaskBatch("future");
            var executor = scene.fixedExecutor("workerPool", 1);

            var future = executor.submitFuture("price", 42);
            assertNull(future.get());
            executor.completeAll();
            assertEquals(42, future.get());
        });

        assertTrue(out.contains("FUTURE_CREATED"), "expected future creation, got:\n" + out);
        assertTrue(out.contains("FUTURE_GET_WAITING"), "expected early get wait, got:\n" + out);
        assertTrue(out.contains("FUTURE_COMPLETED"), "expected future completion, got:\n" + out);
        assertTrue(out.contains("FUTURE_GET_RETURNED"), "expected future result, got:\n" + out);
    }

    @Test
    void emitsShutdownEventAndOnlyTraceLines() {
        String out = captureTrace(() -> {
            VisualTaskBatch scene = new VisualTaskBatch("shutdown");
            var executor = scene.fixedExecutor("workerPool", 1);

            executor.submit("task-1");
            executor.completeAll();
            executor.shutdown();
        });

        assertTrue(out.contains("EXECUTOR_SHUTDOWN"), "expected shutdown event, got:\n" + out);
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
