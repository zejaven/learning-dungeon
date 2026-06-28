package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualCallStackTest {

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
    void unboundedRecursionOverflowsTheStack() {
        String out = captureTrace(() -> {
            VisualCallStack stack = new VisualCallStack(5);
            stack.recurseUntilOverflow("countDown");
        });
        assertTrue(out.contains("STACK_PUSH"), "expected pushes, got:\n" + out);
        assertTrue(out.contains("STACK_OVERFLOW"), "expected an overflow event, got:\n" + out);
    }

    @Test
    void boundedRecursionPopsEveryFrameWithoutOverflow() {
        String out = captureTrace(() -> {
            VisualCallStack stack = new VisualCallStack(6);
            stack.call("factorial");
            stack.call("factorial");
            stack.call("factorial");
            stack.ret();
            stack.ret();
            stack.ret();
        });
        assertTrue(out.contains("STACK_PUSH"), "expected pushes, got:\n" + out);
        assertTrue(out.contains("STACK_POP"), "expected pops, got:\n" + out);
        assertFalse(out.contains("STACK_OVERFLOW"), "a correct recursion must not overflow:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualCallStack stack = new VisualCallStack(4);
            stack.recurseUntilOverflow("ping");
        });
        out.lines().forEach(line ->
                assertTrue(line.isBlank() || line.startsWith("@@TRACE@@"),
                        "unexpected non-trace output: " + line));
    }
}
