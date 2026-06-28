package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualJitTest {

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
    void emitsHotCompileAndOptimizedCallAfterWarmup() {
        String out = captureTrace(() -> {
            VisualJit jit = new VisualJit("test-jvm", 3);
            jit.call("price");
            jit.call("price");
            jit.call("price");
            jit.call("price");
        });

        assertTrue(out.contains("JIT_PROFILE_HOT"), "expected hot profile event, got:\n" + out);
        assertTrue(out.contains("JIT_COMPILE"), "expected compile event, got:\n" + out);
        assertTrue(out.contains("JIT_OPTIMIZED_CALL"), "expected optimized call event, got:\n" + out);
    }

    @Test
    void coldMethodsStayInterpreted() {
        String out = captureTrace(() -> {
            VisualJit jit = new VisualJit("test-jvm", 4);
            jit.call("rareReport");
            jit.call("healthCheck");
            jit.call("cleanup");
        });

        assertTrue(out.contains("JIT_INTERPRET"), "expected interpreted calls, got:\n" + out);
        assertFalse(out.contains("JIT_COMPILE"), "cold methods should not compile:\n" + out);
    }

    @Test
    void emitsInliningEscapeAndDeoptimizationEvents() {
        String out = captureTrace(() -> {
            VisualJit jit = new VisualJit("test-jvm", 2);
            jit.call("checkout");
            jit.call("checkout");
            jit.inline("checkout", "tax");
            jit.eliminateAllocation("checkout", "Money");
            jit.deoptimize("checkout", "new receiver type");
        });

        assertTrue(out.contains("JIT_INLINE"), "expected inline event, got:\n" + out);
        assertTrue(out.contains("JIT_ESCAPE_ELIMINATION"), "expected escape-analysis event, got:\n" + out);
        assertTrue(out.contains("JIT_DEOPTIMIZE"), "expected deoptimization event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualJit jit = new VisualJit("test-jvm", 2);
            jit.call("sum");
            jit.call("sum");
            jit.call("sum");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
