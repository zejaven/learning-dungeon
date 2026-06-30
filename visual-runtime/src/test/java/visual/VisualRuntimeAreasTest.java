package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualRuntimeAreasTest {

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
    void freshJvmHasOneHeapAndOneStack() {
        String out = captureTrace(VisualRuntimeAreas::new);

        assertTrue(out.contains("RUNTIME_SCENE"), "expected scene event, got:\n" + out);
        assertTrue(out.contains("\"heapCount\":1"), "expected one heap, got:\n" + out);
        assertTrue(out.contains("\"stackCount\":1"), "expected one stack, got:\n" + out);
    }

    @Test
    void everyStartedThreadAddsExactlyOneStackAndHeapStaysOne() {
        VisualRuntimeAreas[] holder = new VisualRuntimeAreas[1];
        String out = captureTrace(() -> {
            VisualRuntimeAreas jvm = new VisualRuntimeAreas();
            holder[0] = jvm;
            jvm.startThread("worker-1");
            jvm.startThread("worker-2");
        });

        assertTrue(out.contains("THREAD_STARTED"), "expected thread start, got:\n" + out);
        assertTrue(out.contains("\"stackCount\":3"), "expected 3 stacks, got:\n" + out);
        assertTrue(out.contains("\"heapCount\":1"), "heap must stay one, got:\n" + out);
        assertEquals(3, holder[0].stackCount());
    }

    @Test
    void allocationsFromAnyThreadLandInTheOneHeap() {
        String out = captureTrace(() -> {
            VisualRuntimeAreas jvm = new VisualRuntimeAreas();
            jvm.startThread("worker-1");
            jvm.allocate("main", "Config");
            jvm.allocate("worker-1", "Task");
        });

        assertTrue(out.contains("HEAP_ALLOCATE"), "expected allocation, got:\n" + out);
        assertTrue(out.contains("\"owner\":\"worker-1\""), "expected worker-owned object, got:\n" + out);
        // Both objects are in the single heap array; the last state lists two objects.
        assertTrue(out.contains("\"type\":\"Config\"") && out.contains("\"type\":\"Task\""),
                "expected both objects in the one heap, got:\n" + out);
    }

    @Test
    void finishingAThreadRemovesItsStackButKeepsTheHeap() {
        String out = captureTrace(() -> {
            VisualRuntimeAreas jvm = new VisualRuntimeAreas();
            jvm.startThread("worker-1");
            jvm.allocate("worker-1", "Task");
            jvm.endThread("worker-1");
        });

        assertTrue(out.contains("THREAD_EXITED"), "expected thread exit, got:\n" + out);
        // After the worker exits only main's stack remains, but its heap object stays.
        int lastExit = out.lastIndexOf("THREAD_EXITED");
        String tail = out.substring(lastExit);
        assertTrue(tail.contains("\"stackCount\":1"), "expected back to one stack, got:\n" + tail);
        assertTrue(tail.contains("\"type\":\"Task\""), "heap object must survive, got:\n" + tail);
    }

    @Test
    void pushAndPopTouchOnlyTheNamedThreadStack() {
        String out = captureTrace(() -> {
            VisualRuntimeAreas jvm = new VisualRuntimeAreas();
            jvm.startThread("worker-1");
            jvm.call("worker-1", "process");
            jvm.ret("worker-1");
        });

        assertTrue(out.contains("STACK_PUSH"), "expected push, got:\n" + out);
        assertTrue(out.contains("STACK_POP"), "expected pop, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualRuntimeAreas jvm = new VisualRuntimeAreas();
            jvm.startThread("worker-1");
            jvm.allocate("worker-1", "Task");
            jvm.endThread("worker-1");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
