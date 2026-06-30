package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualMemoryLeakTest {

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
    void unreferencedObjectIsCollected() {
        VisualMemoryLeak heap = new VisualMemoryLeak("healthy");
        String out = captureTrace(() -> {
            heap.allocate("temp", "Temp", "method");
            heap.exitScope("method");
            heap.gc();
        });

        assertTrue(out.contains("GC_COLLECTED"),
                "expected the unreferenced object to be collected, got:\n" + out);
        assertEquals(0, heap.leakCount(), "no leak expected");
        assertEquals(0, heap.liveCount(), "object should be freed");
    }

    @Test
    void staticRootRetainsEscapedObject() {
        VisualMemoryLeak heap = new VisualMemoryLeak("static-cache");
        String out = captureTrace(() -> {
            heap.longLivedRoot("cache");
            heap.allocate("e1", "Entry", "request");
            heap.addReference("e1", "cache");
            heap.exitScope("request");
            heap.gc();
        });

        assertTrue(out.contains("ROOT_DECLARED"), "expected root declaration, got:\n" + out);
        assertTrue(out.contains("REFERENCE_ADDED"), "expected reference added, got:\n" + out);
        assertTrue(out.contains("LEAK_DETECTED"), "expected a detected leak, got:\n" + out);
        assertEquals(1, heap.leakCount(), "one leaked object expected");
    }

    @Test
    void removingTheLastReferenceFixesTheLeak() {
        VisualMemoryLeak heap = new VisualMemoryLeak("fixed-cache");
        String out = captureTrace(() -> {
            heap.longLivedRoot("cache");
            heap.allocate("e1", "Entry", "request");
            heap.addReference("e1", "cache");
            heap.exitScope("request");
            heap.dropReference("e1", "cache");
            heap.gc();
        });

        assertTrue(out.contains("REFERENCE_REMOVED"), "expected reference removal, got:\n" + out);
        assertTrue(out.contains("GC_COLLECTED"), "expected the object to be collected, got:\n" + out);
        assertEquals(0, heap.leakCount(), "no leak after the fix");
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualMemoryLeak heap = new VisualMemoryLeak("prefix");
            heap.longLivedRoot("cache");
            heap.allocate("a", "A", "scope");
            heap.addReference("a", "cache");
            heap.exitScope("scope");
            heap.gc();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
