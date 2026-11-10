package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualBucketTest {

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
    void shortBucketStaysAList() {
        String out = captureTrace(() -> {
            VisualBucket bucket = new VisualBucket(5, 64);
            bucket.add("k0", "v0");
            bucket.add("k1", "v1");
            bucket.get("k1");
        });
        assertTrue(out.contains("LIST_ADD"), "expected LIST_ADD, got:\n" + out);
        assertTrue(out.contains("LIST_GET"), "expected LIST_GET, got:\n" + out);
        assertFalse(out.contains("TREEIFY\""), "should not treeify a short chain:\n" + out);
    }

    @Test
    void longBucketTreeifiesAndSearchesInLogTime() {
        String out = captureTrace(() -> {
            VisualBucket bucket = new VisualBucket(5, 64);
            for (int i = 0; i < 9; i++) {
                bucket.add("k" + i, "v" + i); // the 9th add grows past TREEIFY_THRESHOLD
            }
            bucket.get("k0");
        });
        assertTrue(out.contains("TREEIFY"), "expected TREEIFY, got:\n" + out);
        assertTrue(out.contains("TREE_GET"), "expected TREE_GET, got:\n" + out);
    }

    @Test
    void aChainOfExactlyEightIsStillAChain() {
        // JDK 21 checks after appending: 8 entries stay a list, the 9th treeifies.
        String out = captureTrace(() -> {
            VisualBucket bucket = new VisualBucket(5, 64);
            for (int i = 0; i < 8; i++) {
                bucket.add("k" + i, "v" + i);
            }
        });
        assertFalse(out.contains("\"TREEIFY\""), "8 entries must not treeify:\n" + out);
    }

    @Test
    void smallCapacityResizesInsteadOfTreeifying() {
        String out = captureTrace(() -> {
            VisualBucket bucket = new VisualBucket(0, 16);
            for (int i = 0; i < 9; i++) {
                bucket.add("k" + i, "v" + i);
            }
        });
        assertTrue(out.contains("TREEIFY_SKIPPED"),
                "expected TREEIFY_SKIPPED below MIN_TREEIFY_CAPACITY, got:\n" + out);
        assertFalse(out.contains("\"TREEIFY\""), "should not treeify below capacity 64:\n" + out);
    }

    @Test
    void shrinkingTreeUntreeifies() {
        String out = captureTrace(() -> {
            VisualBucket bucket = new VisualBucket(5, 64);
            for (int i = 0; i < 9; i++) {
                bucket.add("k" + i, "v" + i);
            }
            bucket.remove("k0");
            bucket.remove("k1");
            bucket.remove("k2"); // 6 left: the displayed tree loses its root's left grandchild
        });
        assertTrue(out.contains("UNTREEIFY"), "expected UNTREEIFY, got:\n" + out);
    }

    @Test
    void theShapeRuleIsTheJdkOne() {
        // A root without a child, or a left child without a left child, is "too small".
        assertTrue(VisualBucket.tooSmallByShape(1));
        assertTrue(VisualBucket.tooSmallByShape(2));
        assertTrue(VisualBucket.tooSmallByShape(6));
        assertFalse(VisualBucket.tooSmallByShape(7));
        assertFalse(VisualBucket.tooSmallByShape(9));
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualBucket bucket = new VisualBucket();
            bucket.add("a", "1");
            bucket.get("a");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
