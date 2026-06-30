package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStreamTest {

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
    void intermediateOpsAreLazyUntilTerminalRuns() {
        String out = captureTrace(() -> {
            // Build a pipeline but never call a terminal operation.
            VisualStream.of("numbers", 1, 2, 3)
                    .filter("n > 1", n -> n > 1)
                    .map("n * 10", n -> n * 10);
        });

        assertTrue(out.contains("STREAM_SOURCE"), "expected source event, got:\n" + out);
        assertTrue(out.contains("STREAM_PIPELINE_OP"), "expected pipeline-op event, got:\n" + out);
        assertFalse(out.contains("STREAM_TERMINAL_START"),
                "no terminal was called, so execution must not start:\n" + out);
        assertFalse(out.contains("STREAM_ELEMENT_IN"),
                "intermediate operations must be lazy:\n" + out);
    }

    @Test
    void terminalRunsPipelineAndConsumesStream() {
        StringBuilder result = new StringBuilder();
        String out = captureTrace(() -> {
            List<Integer> collected = VisualStream.of("numbers", 1, 2, 3, 4)
                    .filter("even", n -> n % 2 == 0)
                    .map("times 10", n -> n * 10)
                    .collectToList("collect(toList())");
            result.append(collected);
        });

        assertEquals("[20, 40]", result.toString());
        assertTrue(out.contains("STREAM_TERMINAL_START"), "expected terminal start, got:\n" + out);
        assertTrue(out.contains("STREAM_FILTER_PASS"), "expected a filter pass, got:\n" + out);
        assertTrue(out.contains("STREAM_FILTER_DROP"), "expected a filter drop, got:\n" + out);
        assertTrue(out.contains("STREAM_MAP"), "expected a map event, got:\n" + out);
        assertTrue(out.contains("STREAM_ELEMENT_COLLECTED"), "expected a collected event, got:\n" + out);
        assertTrue(out.contains("STREAM_TERMINAL_RESULT"), "expected a result event, got:\n" + out);
        assertTrue(out.contains("STREAM_CONSUMED"), "expected a consumed event, got:\n" + out);
    }

    @Test
    void findFirstShortCircuits() {
        StringBuilder result = new StringBuilder();
        String out = captureTrace(() -> {
            Optional<Integer> first = VisualStream.of("numbers", 1, 2, 3, 4, 5)
                    .filter("n > 2", n -> n > 2)
                    .findFirst("findFirst()");
            result.append(first.orElse(-1));
        });

        assertEquals("3", result.toString());
        assertTrue(out.contains("STREAM_SHORT_CIRCUIT"), "expected a short-circuit event, got:\n" + out);
        // Element 4 and 5 must never enter the pipeline after the match.
        assertFalse(out.contains("Element 5 enters"), "stream should not pull element 5:\n" + out);
    }

    @Test
    void limitShortCircuitsAfterTakingEnough() {
        StringBuilder result = new StringBuilder();
        String out = captureTrace(() -> {
            List<Integer> taken = VisualStream.of("numbers", 1, 2, 3, 4, 5)
                    .map("n * 2", n -> n * 2)
                    .limit("limit(2)", 2)
                    .collectToList("collect(toList())");
            result.append(taken);
        });

        assertEquals("[2, 4]", result.toString());
        assertTrue(out.contains("STREAM_SHORT_CIRCUIT"), "limit should short-circuit, got:\n" + out);
    }
}
