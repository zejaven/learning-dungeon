package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualNaiveArrayListTest {

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
    void everyAppendGrowsByOneAndCopiesExistingReferences() {
        String out = captureTrace(() -> {
            VisualNaiveArrayList<Integer> list = new VisualNaiveArrayList<>("list");
            list.add(10);
            list.add(20);
            list.add(30);
            list.add(40);
            assertEquals(6, list.totalCopies());
        });

        assertTrue(out.contains("NAIVE_ARRAYLIST_GROW"),
                "expected grow events, got:\n" + out);
        assertTrue(out.contains("\"copied\":3"),
                "expected the fourth append to copy three existing references, got:\n" + out);
        assertTrue(out.contains("\"totalCopies\":6"),
                "expected triangular copy count for four appends, got:\n" + out);
    }

    @Test
    void reportExplainsQuadraticBatchCost() {
        String out = captureTrace(() -> {
            VisualNaiveArrayList<String> list = new VisualNaiveArrayList<>("orders");
            list.add("A");
            list.add("B");
            list.add("C");
            list.reportTotalWork();
        });

        assertTrue(out.contains("NAIVE_ARRAYLIST_TOTAL"),
                "expected a total-work event, got:\n" + out);
        assertTrue(out.contains("\"complexity\":\"O(N^2)\""),
                "expected quadratic complexity in state, got:\n" + out);
        assertTrue(out.contains("N * (N - 1) / 2"),
                "expected triangular formula in trace description/state, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualNaiveArrayList<Integer> list = new VisualNaiveArrayList<>("list");
            list.add(1);
            list.reportTotalWork();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
