package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualTreeSetTest {

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
    void keepsValuesInSortedOrder() {
        AtomicReference<List<Integer>> snapshot = new AtomicReference<>();
        String out = captureTrace(() -> {
            VisualTreeSet<Integer> set = new VisualTreeSet<>("scores");
            set.add(30);
            set.add(10);
            set.add(20);
            snapshot.set(set.values());
        });

        assertTrue(out.contains("TREESET_ADD"), "expected add events, got:\n" + out);
        assertEquals(List.of(10, 20, 30), snapshot.get());
    }

    @Test
    void emitsDuplicateWhenComparatorReturnsZero() {
        AtomicReference<List<String>> snapshot = new AtomicReference<>();
        String out = captureTrace(() -> {
            Comparator<String> byLength = Comparator.comparingInt(String::length);
            VisualTreeSet<String> set = new VisualTreeSet<>("words", byLength, "length Comparator");
            set.add("go");
            set.add("up");
            snapshot.set(set.values());
        });

        assertTrue(out.contains("TREESET_DUPLICATE"),
                "expected duplicate event, got:\n" + out);
        assertEquals(List.of("go"), snapshot.get());
    }

    @Test
    void emitsNavigationAndRangeEvents() {
        AtomicReference<Integer> ceiling = new AtomicReference<>();
        AtomicReference<List<Integer>> range = new AtomicReference<>();
        String out = captureTrace(() -> {
            VisualTreeSet<Integer> set = new VisualTreeSet<>("ages");
            set.add(10);
            set.add(20);
            set.add(30);
            set.add(40);
            ceiling.set(set.ceiling(25));
            range.set(set.range(15, 35));
        });

        assertTrue(out.contains("TREESET_NAVIGATE"),
                "expected navigation event, got:\n" + out);
        assertTrue(out.contains("TREESET_RANGE"),
                "expected range event, got:\n" + out);
        assertEquals(30, ceiling.get());
        assertEquals(List.of(20, 30), range.get());
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualTreeSet<String> set = new VisualTreeSet<>("names");
            set.add("Alice");
            set.add("Bob");
            set.contains("Alice");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
