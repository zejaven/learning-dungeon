package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualRedBlackTreeTest {

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
    void emitsRecolorWhenParentAndUncleAreRed() {
        String out = captureTrace(() -> {
            VisualRedBlackTree tree = new VisualRedBlackTree("orders");
            tree.insert(10);
            tree.insert(5);
            tree.insert(15);
            tree.insert(1);
        });

        assertTrue(out.contains("RBT_RECOLOR"),
                "expected a recolor event, got:\n" + out);
        assertTrue(out.contains("RBT_ROOT_BLACK"),
                "expected root to be forced black after recolor, got:\n" + out);
    }

    @Test
    void emitsLeftRotationForRightRightInsertion() {
        String out = captureTrace(() -> {
            VisualRedBlackTree tree = new VisualRedBlackTree("orders");
            tree.insert(10);
            tree.insert(20);
            tree.insert(30);
        });

        assertTrue(out.contains("RBT_ROTATE_LEFT"),
                "expected a left rotation event, got:\n" + out);
    }

    @Test
    void emitsRightRotationForLeftLeftInsertion() {
        String out = captureTrace(() -> {
            VisualRedBlackTree tree = new VisualRedBlackTree("orders");
            tree.insert(30);
            tree.insert(20);
            tree.insert(10);
        });

        assertTrue(out.contains("RBT_ROTATE_RIGHT"),
                "expected a right rotation event, got:\n" + out);
    }

    @Test
    void emitsSearchAndKeepsValuesSorted() {
        ListHolder holder = new ListHolder();
        captureTrace(() -> {
            VisualRedBlackTree tree = new VisualRedBlackTree("orders");
            tree.insert(40);
            tree.insert(10);
            tree.insert(70);
            tree.insert(50);
            holder.values = tree.values().toString();
        });
        assertEquals("[10, 40, 50, 70]", holder.values);

        String out = captureTrace(() -> {
            VisualRedBlackTree traced = new VisualRedBlackTree("orders");
            traced.insert(40);
            traced.insert(10);
            traced.insert(70);
            traced.insert(50);
            traced.contains(50);
            traced.contains(99);
        });

        assertTrue(out.contains("RBT_SEARCH"),
                "expected search events, got:\n" + out);
        assertTrue(out.contains("\"result\":\"found\""),
                "expected found search state, got:\n" + out);
        assertTrue(out.contains("\"result\":\"missing\""),
                "expected missing search state, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualRedBlackTree tree = new VisualRedBlackTree("orders");
            tree.insert(10);
            tree.insert(20);
            tree.insert(30);
            tree.contains(20);
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }

    private static final class ListHolder {
        String values;
    }
}
