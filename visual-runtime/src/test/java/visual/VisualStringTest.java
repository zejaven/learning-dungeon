package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStringTest {

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
    void deriveCreatesANewObjectAndLeavesTheOriginal() {
        String out = captureTrace(() -> {
            VisualString scene = new VisualString();
            scene.literal("s", "hello");
            scene.concat("s", "!");
        });
        assertTrue(out.contains("STRING_LITERAL"), out);
        assertTrue(out.contains("STRING_DERIVE"), out);
        // The original pooled "hello" survives next to the derived "hello!".
        assertTrue(out.contains("\"value\":\"hello\""), out);
        assertTrue(out.contains("\"value\":\"hello!\""), out);
    }

    @Test
    void replaceDoesNotMutateInPlace() {
        String out = captureTrace(() -> {
            VisualString scene = new VisualString();
            scene.literal("s", "cat");
            scene.replace("s", 'c', 'b');
        });
        assertTrue(out.contains("STRING_DERIVE"), out);
        assertTrue(out.contains("\"value\":\"cat\""), out);
        assertTrue(out.contains("\"value\":\"bat\""), out);
    }

    @Test
    void newStringIsDistinctFromTheLiteralButEqual() {
        String out = captureTrace(() -> {
            VisualString scene = new VisualString();
            scene.literal("a", "hi");
            scene.newString("b", "hi");
            scene.compare("a", "b");
        });
        assertTrue(out.contains("STRING_NEW"), out);
        // == is false (distinct objects) while equals is true (same content).
        assertTrue(out.contains("== b is false"), out);
        assertTrue(out.contains("equals(b) is true"), out);
    }

    @Test
    void builderAppendMutatesTheSameObject() {
        String out = captureTrace(() -> {
            VisualString scene = new VisualString();
            scene.builder("sb", "a");
            scene.append("sb", "b");
            scene.append("sb", "c");
        });
        assertTrue(out.contains("BUILDER_APPEND"), out);
        assertTrue(out.contains("\"value\":\"abc\""), out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualString scene = new VisualString();
            scene.literal("s", "x");
            scene.concat("s", "y");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
        assertFalse(out.isBlank());
    }
}
