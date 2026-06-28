package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStringConcatTest {

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
    void naiveLoopEmitsStepsAndLeavesGarbage() {
        String out = captureTrace(() -> new VisualStringConcat().concatLoop("ab", 5));
        assertTrue(out.contains("CONCAT_STEP"), "expected per-iteration steps, got:\n" + out);
        assertTrue(out.contains("CONCAT_DONE"), "expected a summary event, got:\n" + out);
        // Garbage must accumulate: every iteration after the first drops the old String.
        assertTrue(out.contains("becomes garbage") || out.contains("\"garbageObjects\":4"),
                "expected garbage to pile up, got:\n" + out);
    }

    @Test
    void builderReusesOneBufferAndGrowsWhenFull() {
        // 8 pieces of length 5 = 40 chars > default capacity 16, so it must grow.
        String out = captureTrace(() -> new VisualStringConcat().builderLoop("hello", 8));
        assertTrue(out.contains("BUILDER_START"), "expected a builder start, got:\n" + out);
        assertTrue(out.contains("BUILDER_APPEND"), "expected appends, got:\n" + out);
        assertTrue(out.contains("BUILDER_GROW"), "expected a capacity grow, got:\n" + out);
    }

    @Test
    void preSizedBuilderNeverGrows() {
        // 6 pieces of length 2 = 12 chars, buffer pre-sized to 64: no growth at all.
        String out = captureTrace(() -> new VisualStringConcat().builderLoop("ab", 6, 64));
        assertTrue(out.contains("BUILDER_PRESIZE"), "expected a pre-size event, got:\n" + out);
        assertFalse(out.contains("BUILDER_GROW"), "a pre-sized buffer must not grow, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> new VisualStringConcat().builderLoop("x", 3));
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
