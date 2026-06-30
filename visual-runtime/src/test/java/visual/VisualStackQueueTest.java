package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStackQueueTest {

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
    void stackPopsNewestItemFirst() {
        final String[] removed = new String[1];
        String out = captureTrace(() -> {
            VisualStackQueue<String> stack = VisualStackQueue.stack("plates");
            stack.push("A");
            stack.push("B");
            removed[0] = stack.pop();
        });

        assertEquals("B", removed[0]);
        assertTrue(out.contains("STACK_PUSH"), "expected stack push event, got:\n" + out);
        assertTrue(out.contains("STACK_POP"), "expected stack pop event, got:\n" + out);
        assertTrue(out.contains("\"rule\":\"LIFO\""), "expected LIFO rule in state, got:\n" + out);
    }

    @Test
    void queuePollsOldestItemFirst() {
        final String[] removed = new String[1];
        String out = captureTrace(() -> {
            VisualStackQueue<String> queue = VisualStackQueue.queue("tickets");
            queue.offer("A");
            queue.offer("B");
            removed[0] = queue.poll();
        });

        assertEquals("A", removed[0]);
        assertTrue(out.contains("QUEUE_OFFER"), "expected queue offer event, got:\n" + out);
        assertTrue(out.contains("QUEUE_POLL"), "expected queue poll event, got:\n" + out);
        assertTrue(out.contains("\"rule\":\"FIFO\""), "expected FIFO rule in state, got:\n" + out);
    }

    @Test
    void peekDoesNotRemove() {
        final String[] seen = new String[2];
        String out = captureTrace(() -> {
            VisualStackQueue<String> stack = VisualStackQueue.stack("undo");
            stack.push("type");
            seen[0] = stack.peek();
            seen[1] = stack.pop();
        });

        assertEquals("type", seen[0]);
        assertEquals("type", seen[1]);
        assertTrue(out.contains("STACK_PEEK"), "expected stack peek event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualStackQueue<String> queue = VisualStackQueue.queue("jobs");
            queue.offer("job-1");
            queue.peek();
            queue.poll();
        });

        assertTrue(out.contains("QUEUE_PEEK"), "expected queue peek event, got:\n" + out);
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
