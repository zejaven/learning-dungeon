package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualPrimitiveSizesTest {

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
    void emitsFixedPrimitiveByteSizes() {
        String out = captureTrace(VisualPrimitiveSizes::new);

        assertTrue(out.contains("PRIMITIVE_SIZE_TABLE"), "expected a size table event, got:\n" + out);
        assertTrue(out.contains("\"type\":\"byte\""), "byte row should be present, got:\n" + out);
        assertTrue(out.contains("\"bytes\":1"), "byte should be 1 byte, got:\n" + out);
        assertTrue(out.contains("\"type\":\"int\""), "int row should be present, got:\n" + out);
        assertTrue(out.contains("\"bytes\":4"), "int/float should include 4-byte values, got:\n" + out);
        assertTrue(out.contains("\"type\":\"double\""), "double row should be present, got:\n" + out);
        assertTrue(out.contains("\"bytes\":8"), "long/double should include 8-byte values, got:\n" + out);
        assertTrue(out.contains("\"type\":\"char\""), "char row should be present, got:\n" + out);
    }

    @Test
    void marksBooleanAsNotSpecifiedInBytes() {
        String out = captureTrace(() -> {
            VisualPrimitiveSizes sizes = new VisualPrimitiveSizes();
            sizes.showBooleanCaveat();
        });

        assertTrue(out.contains("PRIMITIVE_BOOLEAN_CAVEAT"),
                "expected a boolean caveat event, got:\n" + out);
        assertTrue(out.contains("\"type\":\"boolean\""), "boolean row should be present, got:\n" + out);
        assertTrue(out.contains("\"bytes\":null"), "boolean should not claim a fixed byte count, got:\n" + out);
        assertTrue(out.contains("\"storage\":\"not-specified\""),
                "boolean storage should be marked not specified, got:\n" + out);
    }

    @Test
    void emitsStorageContextCaveat() {
        String out = captureTrace(() -> {
            VisualPrimitiveSizes sizes = new VisualPrimitiveSizes();
            sizes.showStorageContext();
        });

        assertTrue(out.contains("PRIMITIVE_STORAGE_CONTEXT"),
                "expected a storage context event, got:\n" + out);
        assertTrue(out.contains("object"), "description should mention full footprint context, got:\n" + out);
        assertTrue(out.contains("JVM slots"), "description should mention JVM slots, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualPrimitiveSizes sizes = new VisualPrimitiveSizes();
            sizes.showIntegerFamily();
            sizes.showFloatingFamily();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
