package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHeapTest {

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
    void allocatesObjectsInEden() {
        String out = captureTrace(() -> {
            VisualHeap heap = new VisualHeap("heap");
            heap.allocate("a");
        });
        assertTrue(out.contains("OBJECT_ALLOCATED"),
                "expected an allocation event, got:\n" + out);
    }

    @Test
    void fillingEdenTriggersMinorGc() {
        String out = captureTrace(() -> {
            VisualHeap heap = new VisualHeap("heap");
            // Eden capacity is 4; the fifth allocation forces a minor GC first.
            for (int i = 0; i < 5; i++) {
                heap.allocate("o" + i);
            }
        });
        assertTrue(out.contains("EDEN_FULL"), "expected an EDEN_FULL event, got:\n" + out);
        assertTrue(out.contains("MINOR_GC"), "expected a minor GC event, got:\n" + out);
    }

    @Test
    void deadObjectsAreCollectedSurvivorsPromoted() {
        String out = captureTrace(() -> {
            VisualHeap heap = new VisualHeap("heap");
            VisualHeap.Ref keep = heap.allocate("keep");
            VisualHeap.Ref tmp = heap.allocate("tmp");
            heap.drop(tmp);
            // Default tenuring threshold is 3, so three minor GCs promote 'keep'.
            heap.gc();
            heap.gc();
            heap.gc();
        });
        assertTrue(out.contains("OBJECT_UNREACHABLE"),
                "expected an unreachable event, got:\n" + out);
        assertTrue(out.contains("PROMOTION"),
                "expected a promotion event, got:\n" + out);
    }

    @Test
    void survivorOverflowPromotesPrematurely() {
        String out = captureTrace(() -> {
            VisualHeap heap = new VisualHeap("heap");
            // Four live objects fill Eden; the fifth forces a minor GC where the
            // survivor space (capacity 3) overflows, promoting one early.
            for (int i = 0; i < 5; i++) {
                heap.allocate("o" + i);
            }
        });
        assertTrue(out.contains("PROMOTION"),
                "expected a premature promotion event, got:\n" + out);
    }

    @Test
    void majorGcReclaimsOldGeneration() {
        String out = captureTrace(() -> {
            VisualHeap heap = new VisualHeap("heap");
            VisualHeap.Ref cache = heap.allocate("cache");
            heap.gc();
            heap.gc();
            heap.gc(); // promoted to Old
            heap.drop(cache);
            heap.fullGc();
        });
        assertTrue(out.contains("MAJOR_GC"),
                "expected a major GC event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualHeap heap = new VisualHeap("heap");
            heap.allocate("a");
            heap.gc();
            heap.fullGc();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
