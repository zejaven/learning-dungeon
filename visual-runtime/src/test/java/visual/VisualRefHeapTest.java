package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualRefHeapTest {

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
    void strongReferenceKeepsObjectAliveAcrossGc() {
        String out = captureTrace(() -> {
            VisualRefHeap heap = new VisualRefHeap();
            heap.allocate("A", "Photo");
            heap.strong("s", "A");
            heap.gc(); // strong -> survives
        });
        assertTrue(out.contains("REF_STRONG"), out);
        assertFalse(out.contains("REF_COLLECT"), "strongly reachable object must survive:\n" + out);
    }

    @Test
    void weakReferenceIsClearedOnGc() {
        String out = captureTrace(() -> {
            VisualRefHeap heap = new VisualRefHeap();
            heap.allocate("A", "Photo");
            heap.weak("w", "A");
            heap.gc(); // weak-only -> collected
        });
        assertTrue(out.contains("REF_WEAK"), out);
        assertTrue(out.contains("REF_COLLECT"), "weakly reachable object must be collected:\n" + out);
    }

    @Test
    void softCacheSurvivesNormalGcButNotMemoryPressure() {
        String survive = captureTrace(() -> {
            VisualRefHeap heap = new VisualRefHeap();
            heap.allocate("img", "BigImage");
            heap.soft("cache", "img");
            heap.gc(); // normal GC keeps soft
        });
        assertFalse(survive.contains("REF_COLLECT"), "soft survives normal GC:\n" + survive);

        String pressure = captureTrace(() -> {
            VisualRefHeap heap = new VisualRefHeap();
            heap.allocate("img", "BigImage");
            heap.soft("cache", "img");
            heap.gcUnderMemoryPressure(); // soft cleared under pressure
        });
        assertTrue(pressure.contains("REF_GC_PRESSURE"), pressure);
        assertTrue(pressure.contains("REF_COLLECT"), "soft cache is cleared under memory pressure:\n" + pressure);
    }

    @Test
    void phantomGetIsAlwaysNullAndEnqueuedAfterCollection() {
        String out = captureTrace(() -> {
            VisualRefHeap heap = new VisualRefHeap();
            heap.allocate("R", "NativeBuffer");
            heap.strong("s", "R");
            heap.phantom("ph", "R");
            heap.get("ph"); // always null
            heap.drop("s");
            heap.gc(); // collected -> phantom enqueued
        });
        assertTrue(out.contains("REF_PHANTOM"), out);
        assertTrue(out.contains("REF_ENQUEUE"), "phantom reference must be enqueued after collection:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualRefHeap heap = new VisualRefHeap();
            heap.allocate("A", "Photo");
            heap.strong("s", "A");
            heap.get("s");
            heap.drop("s");
            heap.gc();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
