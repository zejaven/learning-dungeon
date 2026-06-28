package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualArrayIndexingTest {

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
    void computesAddressAndDereferencesForValidIndex() {
        String out = captureTrace(() -> {
            VisualArrayIndexing list = new VisualArrayIndexing("names", 4);
            list.store("Alice");
            list.store("Bob");
            list.store("Carol");
            assertEquals("Carol", list.get(2));
        });
        assertTrue(out.contains("ARRAY_STORE_REF"),
                "expected a store event, got:\n" + out);
        assertTrue(out.contains("ARRAY_ADDRESS_CALC"),
                "expected an address-calc event, got:\n" + out);
        assertTrue(out.contains("ARRAY_READ_REF"),
                "expected a read-reference event, got:\n" + out);
        // base 0x1000 + header 16 + index 2 * scale 4 = 4120 = 0x1018.
        assertTrue(out.contains("0x1018"),
                "expected slot 2 to resolve to 0x1018, got:\n" + out);
    }

    @Test
    void rejectsOutOfBoundsBeforeAnyAddressMath() {
        String out = captureTrace(() -> {
            VisualArrayIndexing list = new VisualArrayIndexing("small", 4);
            list.store("X");
            assertThrows(IndexOutOfBoundsException.class, () -> list.get(5));
        });
        assertTrue(out.contains("ARRAY_BOUNDS_CHECK"),
                "expected a bounds-check event, got:\n" + out);
        assertTrue(out.contains("ARRAY_OUT_OF_BOUNDS"),
                "expected an out-of-bounds event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualArrayIndexing list = new VisualArrayIndexing("t", 4);
            list.store("A");
            list.get(0);
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
